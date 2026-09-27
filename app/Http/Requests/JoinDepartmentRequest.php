<?php

namespace App\Http\Requests;

use Illuminate\Foundation\Http\FormRequest;

class JoinDepartmentRequest extends FormRequest
{
    /**
     * Get the validation rules that apply to the request.
     *
     * @return array<string, array<int, string>>
     */
    public function rules(): array
    {
        return [
            'department' => ['required', 'string', 'max:100'],
            'code' => ['required', 'string', 'max:64'],
        ];
    }
}
