<?php

namespace App\Notifications;

use App\Models\Department;
use App\Models\Organization;
use App\Models\User;
use Illuminate\Notifications\Messages\MailMessage;
use Illuminate\Notifications\Notification;

/**
 * Tells someone the organization owner locked their access, to one department or to the
 * whole organization (when there is no department).
 */
class AccessLocked extends Notification
{
    public function __construct(
        public Organization $organization,
        public ?Department $department,
        public User $lockedBy,
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
        $app = config('app.name');
        $scope = $this->department
            ? "the {$this->department->name} department of {$this->organization->name}"
            : $this->organization->name;

        return (new MailMessage)
            ->subject('Your access to '.($this->department?->name ?? $this->organization->name).' was locked')
            ->greeting("Hello {$notifiable->name},")
            ->line("{$this->lockedBy->name} has locked your access to {$scope} on {$app}.")
            ->line($this->department
                ? 'You can no longer open this department or see its files. Your other departments are not affected.'
                : 'You can no longer open any department of this organization or see their files.')
            ->line("If you think this is a mistake, please contact {$this->lockedBy->name}.");
    }
}
