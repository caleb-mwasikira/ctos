<?php

namespace App\Notifications;

use App\Models\Department;
use App\Models\User;
use Illuminate\Notifications\Messages\MailMessage;
use Illuminate\Notifications\Notification;

/**
 * Tells someone a department was shared with them. It is sent to an email address, which
 * may or may not belong to an account yet, so it is used as an on-demand notification.
 */
class DepartmentShared extends Notification
{
    /**
     * @param  bool  $hasAccess  true when the person already has access, false when they must still sign up
     */
    public function __construct(
        public Department $department,
        public User $inviter,
        public string $email,
        public bool $hasAccess,
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

        $message = (new MailMessage)
            ->subject("{$this->inviter->name} shared {$this->department->name} with you")
            ->greeting('Hello!')
            ->line("{$this->inviter->name} has given you access to the {$this->department->name} department of {$organization->name} on {$app}.");

        if ($this->hasAccess) {
            return $message
                ->action('Open the department', route('departments.show', $this->department))
                ->line('It is already in your account. Log in to see its files.');
        }

        return $message
            ->action('Create your account', route('register'))
            ->line("Sign up with this address ({$this->email}) and confirm it, and the department appears on your dashboard. You do not need an access code.");
    }
}
