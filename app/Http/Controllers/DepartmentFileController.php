<?php

namespace App\Http\Controllers;

use App\Models\Department;
use App\Models\Node;
use Illuminate\Http\RedirectResponse;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\Gate;
use Illuminate\Support\Facades\Http;
use Illuminate\Support\Facades\Storage;
use Illuminate\Support\Str;
use Symfony\Component\HttpFoundation\StreamedResponse;

class DepartmentFileController extends Controller
{
    /**
     * Open one file of a department in the browser: a PDF, image, or anything else the browser
     * knows how to display appears there directly, instead of being saved to disk. Members (and
     * only members) may.
     */
    public function show(Request $request, Department $department): StreamedResponse
    {
        $file = $this->findFile($request, $department);

        return Storage::disk('local')->response($file->storage_path, $file->name);
    }

    /**
     * Download one file of a department, saving it to disk under its own name instead of opening
     * it. Members (and only members) may.
     */
    public function download(Request $request, Department $department): StreamedResponse
    {
        $file = $this->findFile($request, $department);

        return Storage::disk('local')->download($file->storage_path, $file->name);
    }

    /**
     * Upload one or more files (or a whole folder, dropped or chosen at once) into the folder
     * ?path= is showing. Members (and only members) may, the same as everyone who can see the
     * files at all.
     *
     * The website has no file storage or node-writing logic of its own (see App\Models\Node's own
     * doc comment): each file's bytes are relayed to the Go API exactly as the desktop and Android
     * apps upload, over a Sanctum token minted for this one request and deleted again once it is
     * done, so nothing longer-lived is left behind for a browser to have held onto.
     */
    public function upload(Request $request, Department $department): RedirectResponse
    {
        Gate::authorize('view', $department);

        $currentPath = $request->string('path')->toString();
        abort_unless($currentPath === '' || Node::isValidPath($currentPath), 404);

        $request->validate([
            'files' => ['required', 'array', 'min:1'],
            'files.*' => ['file'],
        ]);

        // The relative path of each file within whatever was dropped or chosen (empty for a lone
        // file), in the same order as files[]; see resources/js/app.js.
        $relativePaths = json_decode($request->string('paths')->toString(), true);
        $relativePaths = is_array($relativePaths) ? $relativePaths : [];

        $token = $request->user()->createToken('web-upload');
        $uploaded = 0;
        $errors = [];

        try {
            foreach ($request->file('files', []) as $index => $file) {
                $relative = trim(str_replace('\\', '/', $relativePaths[$index] ?? $file->getClientOriginalName()), '/');
                $nodePath = trim(($currentPath !== '' ? $currentPath.'/' : '').$relative, '/');

                if ($relative === '' || ! Node::isValidPath($nodePath)) {
                    $errors[] = "\"{$relative}\" cannot be uploaded: that name is not allowed.";

                    continue;
                }

                $response = Http::withToken($token->plainTextToken)
                    ->withHeaders(['X-Modified-At' => (string) now()->getTimestampMs()])
                    ->withBody(file_get_contents($file->getRealPath()) ?: '', $file->getMimeType() ?: 'application/octet-stream')
                    ->put($this->goUploadUrl($department, $nodePath));

                if ($response->status() === 409) {
                    $errors[] = "\"{$relative}\" already exists in this folder.";
                } elseif (! $response->successful()) {
                    $errors[] = "\"{$relative}\" could not be uploaded: ".($response->json('message') ?? 'the server refused it.');
                } else {
                    $uploaded++;
                }
            }
        } finally {
            $token->accessToken->delete();
        }

        $flash = ['upload_errors' => $errors];
        if ($uploaded > 0) {
            $flash['status'] = "{$uploaded} ".Str::plural('file', $uploaded).' uploaded.';
        }

        return back()->with($flash);
    }

    private function goUploadUrl(Department $department, string $path): string
    {
        $base = rtrim((string) config('services.ctos_api.url'), '/');

        return "{$base}/departments/{$department->slug}/files?".http_build_query(['path' => $path, 'base_version' => 0]);
    }

    /**
     * Find the file at ?path=, checked the same way for showing and downloading it: the user
     * must be a member of the department, and the path a live file that really has content.
     */
    private function findFile(Request $request, Department $department): Node
    {
        Gate::authorize('view', $department);

        $path = $request->string('path')->toString();
        abort_unless(Node::isValidPath($path), 404);

        $file = $department->nodes()->live()->where('type', Node::FILE)->where('path', $path)->first();
        abort_if($file === null || $file->storage_path === null, 404);
        abort_unless(Storage::disk('local')->exists($file->storage_path), 404);

        return $file;
    }
}
