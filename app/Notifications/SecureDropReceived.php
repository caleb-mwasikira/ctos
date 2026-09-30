<?php

namespace App\Notifications;

use App\Models\SecureDrop;
use App\Models\User;
use Illuminate\Notifications\Messages\MailMessage;
use Illuminate\Notifications\Notification;
use Illuminate\Support\Facades\Storage;

/**
 * Tells a department member someone sent them a file, encrypted (see docs/secure-drop.md). The
 * still-encrypted ciphertext is safe to attach directly: nothing but the recipient's own private
 * key (never sent anywhere) and passphrase can ever open it, not even this email. A link to the
 * website is included too, which is the easier way in most people will actually use.
 */
class SecureDropReceived extends Notification
{
    /** Above this, the ciphertext is left off the email (most mail servers refuse anything near this anyway) and only the link is sent. */
    private const MAX_ATTACHMENT_BYTES = 10 * 1024 * 1024;

    public function __construct(
        public SecureDrop $secureDrop,
        public User $sender,
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
            ->line("{$this->sender->name} sent you \"{$this->secureDrop->name}\" through {$app}'s Secure Drop, encrypted on their own device before it ever left it.")
            ->action('Open it on '.$app, route('secure-drop.index'))
            ->line('Opening it there asks for your Secure Drop passphrase once, decrypts the file in your browser, and saves the result to your device.');

        if ($this->secureDrop->size <= self::MAX_ATTACHMENT_BYTES) {
            $message
                ->line('It is also attached below, still fully encrypted — safe to have sitting in an inbox, since only your passphrase can open it.')
                ->attachData(
                    Storage::disk('local')->get($this->secureDrop->storage_path),
                    $this->secureDrop->name.'.enc',
                    ['mime' => 'application/octet-stream'],
                );
        }

        return $message;
    }
}
