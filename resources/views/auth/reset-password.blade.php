<x-guest-layout title="Reset password" heading="Choose a new password" subheading="You will be signed out of every device and app.">
    <form method="POST" action="{{ route('password.update') }}" class="space-y-5">
        @csrf
        <input type="hidden" name="token" value="{{ $token }}">

        <x-field name="email" label="Email" type="email" :value="$email" required autocomplete="username" />
        <x-field name="password" label="New password" type="password" required autofocus autocomplete="new-password" hint="At least 8 characters." />
        <x-field name="password_confirmation" label="Confirm new password" type="password" required autocomplete="new-password" />

        <x-button class="w-full" size="lg">Reset password</x-button>
    </form>
</x-guest-layout>
