<?php

namespace App\Enums;

enum DepartmentRole: string
{
    case Admin = 'admin';
    case Lead = 'lead';
    case Member = 'member';
}
