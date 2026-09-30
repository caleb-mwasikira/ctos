<?php

namespace App\Notifications;

use App\Models\SecureDrop;
use App\Models\User;
use Illuminate\Notifications\Messages\MailMessage;
use Illuminate\Notifications\Notification;
use Illuminate\Support\Facades\Storage;

/**
 * Tells someone with no account yet that a file was sent to them, encrypted (see
 * docs/secure-drop.md, "sending by email"). It is sent to an email address that may not belong
 * to an account, so it is used as an on-demand notification, the same way {@see DepartmentShared}
 * is.
 *
 * This is the one place in the whole feature the server sees a plaintext secret: since there is
 * no account yet to hold a private key, the file's key is instead protected with a one-time
 * password — and the server has to know that password itself in order to put it in this email.
 * Every other way of sending stays end-to-end: the server only ever moves ciphertext and
 * RSA-wrapped keys it cannot open.
 */
class SecureDropSharedByPassword extends Notification
{
    /** Above this, the ciphertext is left off the email (most mail servers refuse anything near this anyway) and only the link is sent. */
    private const MAX_ATTACHMENT_BYTES = 10 * 1024 * 1024;

    public function __construct(
        public SecureDrop $secureDrop,
        public User $sender,
        public string $email,
        public string $token,
        public string $password,
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

        $message = (new MailMessage)
            ->subject("{$this->sender->name} sent you a file, encrypted")
            ->greeting('Hello!')
            ->line("{$this->sender->name} sent you \"{$this->secureDrop->name}\" through {$app}'s Secure Drop.")
            ->line("You do not have a {$app} account yet, so instead of your own key, it is protected with a one-time password:")
            ->line("**{$this->password}**")
            ->action('Open it', route('secure-drop.shared.show', $this->token))
            ->line('Anyone with this email and that password can open it, so treat it the way you would the file itself.');

        if ($this->secureDrop->size <= self::MAX_ATTACHMENT_BYTES) {
            $message
                ->line('It is also attached below, still fully encrypted.')
                ->attachData(
                    Storage::disk('local')->get($this->secureDrop->storage_path),
                    $this->secureDrop->name.'.enc',
                    ['mime' => 'application/octet-stream'],
                );
        }

        // Only one button fits the template, already used above, so this second link is plain
        // Markdown text instead (the notification mail template renders it as a real link).
        return $message->line(
            "Don't have an account yet? [Create one](".route('register').") with this address ({$this->email}) and set up your own Secure Drop key, ".
            'and next time a file like this one arrives already yours to open — no password needed.',
        );
    }
}
