<x-guest-layout title="Sign up" heading="Create your account" subheading="Set up an organization or join your team's department.">
    <form method="POST" action="{{ route('register') }}" class="space-y-5">
        @csrf
        <x-field name="name" label="Full name" required autofocus autocomplete="name" />
        <x-field name="email" label="Email" type="email" :value="request('email')" required autocomplete="username" />
        <x-field name="password" label="Password" type="password" required autocomplete="new-password" hint="At least 8 characters." />
        <x-field name="password_confirmation" label="Confirm password" type="password" required autocomplete="new-password" />

        <x-button class="w-full" size="lg">Create account</x-button>
    </form>

    <p class="mt-8 text-center text-sm text-slate-600 dark:text-slate-400">
        Already registered?
        <a href="{{ route('login') }}" class="font-semibold text-brand-600 hover:text-brand-500 dark:text-brand-300">Log in</a>
    </p>
</x-guest-layout>
