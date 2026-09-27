<?php

use App\Enums\DepartmentRole;
use App\Models\Department;
use App\Models\Organization;
use App\Models\User;
use Illuminate\Http\UploadedFile;
use Illuminate\Support\Facades\Http;

beforeEach(function () {
    config(['services.ctos_api.url' => 'http://go-api.test/api/v1']);
    $this->organization = Organization::factory()->create();
    $this->department = Department::factory()->for($this->organization)->create(['name' => 'Engineering']);
    $this->member = User::factory()->create();
    $this->department->members()->attach($this->member->id, ['role' => DepartmentRole::Member->value]);
});

/** A Go API answer to one upload: 201 with a node, matching what ctos_api actually sends. */
function fakeGoUploadResponse(string $path, int $version = 1)
{
    return Http::response([
        'data' => [
            'id' => 1, 'path' => $path, 'type' => 'file', 'size' => 1,
            'checksum' => str_repeat('a', 64), 'version' => $version, 'modified_at' => now()->getTimestampMs(), 'deleted' => false,
        ],
    ], $version === 1 ? 201 : 200);
}

it('uploads a single file to the folder being looked at', function () {
    Http::fake(['go-api.test/*' => fakeGoUploadResponse('report.txt')]);

    $response = $this->actingAs($this->member)->post(route('departments.files.upload', $this->department), [
        'path' => '',
        'paths' => json_encode(['report.txt']),
        'files' => [UploadedFile::fake()->createWithContent('report.txt', 'hello')],
    ]);

    $response->assertRedirect()->assertSessionHas('status', '1 file uploaded.');
    Http::assertSent(function ($request) {
        return $request->url() === 'http://go-api.test/api/v1/departments/engineering/files?path=report.txt&base_version=0'
            && $request->method() === 'PUT'
            && $request->body() === 'hello';
    });
});

it('uploads a folder, keeping each file at its place inside it', function () {
    Http::fake(['go-api.test/*' => fakeGoUploadResponse('anything')]);

    $this->actingAs($this->member)->post(route('departments.files.upload', $this->department), [
        'path' => 'reports',
        'paths' => json_encode(['Q3/summary.md', 'Q3/data/numbers.csv']),
        'files' => [
            UploadedFile::fake()->createWithContent('summary.md', '# Q3'),
            UploadedFile::fake()->createWithContent('numbers.csv', '1,2,3'),
        ],
    ]);

    Http::assertSent(fn ($request) => str_contains($request->url(), 'path=reports%2FQ3%2Fsummary.md'));
    Http::assertSent(fn ($request) => str_contains($request->url(), 'path=reports%2FQ3%2Fdata%2Fnumbers.csv'));
});

it('sends the token it made only for this request, and it does not outlive the request', function () {
    Http::fake(['go-api.test/*' => function ($request) {
        expect($request->header('Authorization')[0] ?? null)->toStartWith('Bearer ');

        return fakeGoUploadResponse('a.txt');
    }]);

    $this->actingAs($this->member)->post(route('departments.files.upload', $this->department), [
        'path' => '', 'paths' => json_encode(['a.txt']),
        'files' => [UploadedFile::fake()->createWithContent('a.txt', 'x')],
    ]);

    expect($this->member->fresh()->tokens()->count())->toBe(0);
});

it('reports a file the server refused without failing the rest of the upload', function () {
    $calls = 0;
    Http::fake(['go-api.test/*' => function () use (&$calls) {
        $calls++;

        return $calls === 1 ? Http::response(['message' => 'This file changed on the server since you last saw it.', 'current' => null], 409) : fakeGoUploadResponse('ok.txt');
    }]);

    $response = $this->actingAs($this->member)->post(route('departments.files.upload', $this->department), [
        'path' => '',
        'paths' => json_encode(['taken.txt', 'ok.txt']),
        'files' => [
            UploadedFile::fake()->createWithContent('taken.txt', 'x'),
            UploadedFile::fake()->createWithContent('ok.txt', 'y'),
        ],
    ]);

    $response->assertSessionHas('status', '1 file uploaded.');
    $response->assertSessionHas('upload_errors', fn ($errors) => str_contains($errors[0], 'taken.txt') && str_contains($errors[0], 'already exists'));
});

it('refuses a path that climbs out of the department', function () {
    Http::fake(['go-api.test/*' => fakeGoUploadResponse('x')]);

    $response = $this->actingAs($this->member)->post(route('departments.files.upload', $this->department), [
        'path' => '', 'paths' => json_encode(['../../etc/passwd']),
        'files' => [UploadedFile::fake()->createWithContent('passwd', 'x')],
    ]);

    $response->assertSessionHas('upload_errors', fn ($errors) => count($errors) === 1 && str_contains($errors[0], 'not allowed'));
    Http::assertNothingSent();
});

it('requires at least one file', function () {
    $response = $this->actingAs($this->member)->post(route('departments.files.upload', $this->department), ['path' => '']);

    $response->assertSessionHasErrors('files');
});

it('checks the folder being uploaded into is a real one', function () {
    $response = $this->actingAs($this->member)->post(route('departments.files.upload', $this->department), [
        'path' => '../escape',
        'files' => [UploadedFile::fake()->create('a.txt', 1)],
    ]);

    $response->assertNotFound();
});

it('does not let outsiders, locked members or guests upload', function () {
    Http::fake();
    $url = route('departments.files.upload', $this->department);
    $payload = ['path' => '', 'files' => [UploadedFile::fake()->create('a.txt', 1)]];
    $locked = User::factory()->create();
    $this->department->members()->attach($locked->id, ['role' => 'member', 'locked_at' => now()]);

    $this->actingAs(User::factory()->create())->post($url, $payload)->assertForbidden();
    $this->actingAs($locked)->post($url, $payload)->assertForbidden();
    auth()->logout();
    $this->post($url, $payload)->assertRedirect(route('login'));
    Http::assertNothingSent();
});
