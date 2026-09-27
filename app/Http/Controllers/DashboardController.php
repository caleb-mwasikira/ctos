<?php

namespace App\Http\Controllers;

use App\Models\Organization;
use Illuminate\Http\Request;
use Illuminate\View\View;

class DashboardController extends Controller
{
    /**
     * An overview with shortcuts to the organizations and departments pages, which each own the
     * actions and listings that used to live here.
     */
    public function __invoke(Request $request): View
    {
        $user = $request->user();

        return view('dashboard', [
            'organizationsCount' => Organization::visibleTo($user)->count(),
            'departmentsCount' => $user->departments()->count(),
        ]);
    }
}
