<?php

use App\Enums\DepartmentRole;
use App\Models\Department;
use App\Models\Node;
use App\Models\Organization;
use App\Models\User;
use Illuminate\Support\Facades\Storage;

beforeEach(function () {
    Storage::fake('local');
    $this->organization = Organization::factory()->create();
    $this->department = Department::factory()->for($this->organization)->create(['name' => 'Engineering']);
    $this->member = User::factory()->create();
    $this->department->members()->attach($this->member->id, ['role' => DepartmentRole::Member->value]);
});

/** Adds a node to the department, with real content on the disk for a file. */
function addNode(Department $department, string $path, string $type = 'file', ?string $content = null, array $attributes = []): Node
{
    $factory = Node::factory()->for($department)->at($path);
    $factory = $type === 'folder' ? $factory->folder() : $factory;

    $node = $factory->create($attributes);

    if ($type === 'file' && $content !== null) {
        $node->update(['storage_path' => "nodes/{$department->id}/".$node->id, 'size' => strlen($content)]);
        Storage::disk('local')->put($node->storage_path, $content);
    }

    return $node->fresh();
}

// ── the explorer ───────────────────────────────────────────────────────────

it('lists the files and folders at the top of the department, folders first', function () {
    addNode($this->department, 'zebra.txt', content: 'z');
    addNode($this->department, 'Apple.txt', content: 'a');
    addNode($this->department, 'reports', 'folder');
    addNode($this->department, 'archive', 'folder');

    $page = $this->actingAs($this->member)->get(route('departments.show', $this->department))->assertOk();

    $page->assertSeeInOrder(['archive', 'reports', 'Apple.txt', 'zebra.txt']);
});

it('shows the size of a file for people to read', function () {
    addNode($this->department, 'big.bin', content: str_repeat('x', 1_500_000));

    $this->actingAs($this->member)->get(route('departments.show', $this->department))->assertSee('1.4 MB');
});

it('shows the contents of a folder and the way back', function () {
    addNode($this->department, 'reports', 'folder');
    addNode($this->department, 'reports/2026', 'folder');
    addNode($this->department, 'reports/summary.md', content: '# hi');
    addNode($this->department, 'reports/2026/q3.xlsx', content: 'numbers');
    addNode($this->department, 'top-level.txt', content: 'top');

    $page = $this->actingAs($this->member)->get(route('departments.show', ['department' => $this->department, 'path' => 'reports']));

    $page->assertOk()
        ->assertSee('summary.md')->assertSee('2026')
        ->assertDontSee('q3.xlsx')->assertDontSee('top-level.txt')
        ->assertSee('href="'.route('departments.show', ['department' => $this->department, 'path' => 'reports/2026']).'"', false)
        ->assertSee('href="'.route('departments.show', $this->department).'"', false);
});

it('does not show deleted files or the files of other departments', function () {
    addNode($this->department, 'gone.txt', content: 'x', attributes: ['deleted_at' => now()]);
    addNode(Department::factory()->for($this->organization)->create(), 'elsewhere.txt', content: 'x');
    addNode($this->department, 'kept.txt', content: 'x');

    $this->actingAs($this->member)->get(route('departments.show', $this->department))
        ->assertSee('kept.txt')->assertDontSee('gone.txt')->assertDontSee('elsewhere.txt');
});

it('shows an empty state for a department and for a folder with nothing in it', function () {
    $this->actingAs($this->member)->get(route('departments.show', $this->department))->assertSee('No files yet');

    addNode($this->department, 'empty', 'folder');
    $this->actingAs($this->member)->get(route('departments.show', ['department' => $this->department, 'path' => 'empty']))->assertSee('This folder is empty');
});

it('answers not found for a folder that does not exist, is deleted, or is really a file', function () {
    addNode($this->department, 'file.txt', content: 'x');
    addNode($this->department, 'old', 'folder', attributes: ['deleted_at' => now()]);

    foreach (['nope', 'old', 'file.txt', '../secret', 'a//b', '..'] as $path) {
        $this->actingAs($this->member)->get(route('departments.show', ['department' => $this->department, 'path' => $path]))->assertNotFound();
    }
});

// ── polling for live updates ────────────────────────────────────────────────

it('serves just the file list, for the department page to poll', function () {
    addNode($this->department, 'reports', 'folder');
    addNode($this->department, 'top-level.txt', content: 'top');

    $response = $this->actingAs($this->member)->get(route('departments.explorer', $this->department));

    $response->assertOk()->assertSee('reports')->assertSee('top-level.txt');
    $response->assertDontSee('<html', false)->assertDontSee($this->department->organization->name);
});

it('polls a folder the same way it is browsed, and obeys the same rules', function () {
    addNode($this->department, 'reports', 'folder');
    addNode($this->department, 'reports/summary.md', content: '# hi');
    addNode($this->department, 'top-level.txt', content: 'top');

    $this->actingAs($this->member)
        ->get(route('departments.explorer', ['department' => $this->department, 'path' => 'reports']))
        ->assertOk()->assertSee('summary.md')->assertDontSee('top-level.txt');

    $this->actingAs($this->member)
        ->get(route('departments.explorer', ['department' => $this->department, 'path' => 'nope']))
        ->assertNotFound();
});

it('does not let outsiders or guests poll a department they cannot see', function () {
    $url = route('departments.explorer', $this->department);

    $this->actingAs(User::factory()->create())->get($url)->assertForbidden();
    auth()->logout();
    $this->get($url)->assertRedirect(route('login'));
});

// ── opening a file in the browser ───────────────────────────────────────────

it('shows a file inline instead of downloading it when opened', function () {
    addNode($this->department, 'reports/summary.md', content: '# Summary');

    $response = $this->actingAs($this->member)->get(route('departments.files.show', ['department' => $this->department, 'path' => 'reports/summary.md']));

    $response->assertOk();
    expect($response->headers->get('Content-Disposition'))->toContain('inline');
    expect($response->streamedContent())->toBe('# Summary');
});

it('checks the same rules for opening a file as for downloading it', function () {
    addNode($this->department, 'folder', 'folder');
    $url = fn (string $path) => route('departments.files.show', ['department' => $this->department, 'path' => $path]);

    foreach (['folder', 'nope.txt', '../../.env', ''] as $path) {
        $this->actingAs($this->member)->get($url($path))->assertNotFound();
    }

    $locked = User::factory()->create();
    $this->department->members()->attach($locked->id, ['role' => 'member', 'locked_at' => now()]);
    $this->actingAs($locked)->get($url('folder'))->assertForbidden();
});

// ── downloads ──────────────────────────────────────────────────────────────

it('lets a member download a file with its own name', function () {
    addNode($this->department, 'reports/summary.md', content: '# Summary');

    $response = $this->actingAs($this->member)->get(route('departments.files.download', ['department' => $this->department, 'path' => 'reports/summary.md']));

    $response->assertOk()->assertDownload('summary.md');
    expect($response->streamedContent())->toBe('# Summary');
});

it('does not let outsiders, locked members or guests download', function () {
    addNode($this->department, 'secret.txt', content: 'classified');
    $url = route('departments.files.download', ['department' => $this->department, 'path' => 'secret.txt']);
    $locked = User::factory()->create();
    $this->department->members()->attach($locked->id, ['role' => 'member', 'locked_at' => now()]);

    $this->actingAs(User::factory()->create())->get($url)->assertForbidden();
    $this->actingAs($locked)->get($url)->assertForbidden();
    auth()->logout();
    $this->get($url)->assertRedirect(route('login'));
});

it('does not download folders, deleted files, missing files or paths that climb out of the department', function () {
    addNode($this->department, 'folder', 'folder');
    addNode($this->department, 'deleted.txt', content: 'x', attributes: ['deleted_at' => now()]);
    addNode($this->department, 'ghost.txt'); // a row, but no content on the disk

    foreach (['folder', 'deleted.txt', 'ghost.txt', 'nope.txt', '../../.env', '/etc/passwd', ''] as $path) {
        $this->actingAs($this->member)
            ->get(route('departments.files.download', ['department' => $this->department, 'path' => $path]))
            ->assertNotFound();
    }
});

it('does not hand out a file of another department by its path', function () {
    $other = Department::factory()->for($this->organization)->create();
    addNode($other, 'other.txt', content: 'not yours');

    $this->actingAs($this->member)
        ->get(route('departments.files.download', ['department' => $this->department, 'path' => 'other.txt']))
        ->assertNotFound();
});

// ── the path rules ─────────────────────────────────────────────────────────

it('accepts and refuses paths by the same rules the API uses', function (string $path, bool $valid) {
    expect(Node::isValidPath($path))->toBe($valid);
})->with([
    ['a.txt', true],
    ['reports/2026/q3.xlsx', true],
    ['with space/and ünïcode.txt', true],
    ['', false],
    ['/absolute', false],
    ['trailing/', false],
    ['a//b', false],
    ['a/./b', false],
    ['../up', false],
    ['a/../b', false],
    ['back\\slash', false],
    ["control\x01char", false],
]);
