<?php

namespace App\Notifications;

use App\Models\Department;
use App\Models\User;
use Illuminate\Notifications\Messages\MailMessage;
use Illuminate\Notifications\Notification;

/**
 * Tells a member that the organization owner made them a department lead.
 */
class DepartmentLeadAssigned extends Notification
{
    public function __construct(
        public Department $department,
        public User $assignedBy,
    ) {}

    /**
     * @return array<int, string>
     */
    public function via(object $notifiable): array
    {
        return ['mail'];
    }

    public function toMail(object $notifiable): MailMessage
    {
        $organization = $this->department->organization;
        $app = config('app.name');

        return (new MailMessage)
            ->subject("You are now a lead of {$this->department->name}")
            ->greeting("Hello {$notifiable->name}!")
            ->line("{$this->assignedBy->name} made you a lead of the {$this->department->name} department of {$organization->name} on {$app}.")
            ->line('As a lead you can share the department with people by email and manage its access code.')
            ->action('Open the department', route('departments.show', $this->department));
    }
}
