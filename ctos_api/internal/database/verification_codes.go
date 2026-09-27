package database

import (
	"context"
	"time"
)

// verification_codes: a short numeric code emailed to confirm an address or reset a password
// from the desktop and Android apps, so they can offer a native "enter the code" screen instead
// of a link that opens a browser. One row per (email, purpose); asking for a new code just
// replaces the old one.

const (
	PurposeEmailVerify   = "email_verify"
	PurposePasswordReset = "password_reset"
)

type VerificationCode struct {
	CodeHash  string
	CreatedAt time.Time
}

// SetVerificationCode replaces whatever code that email already had for that purpose.
func (db *Database) SetVerificationCode(ctx context.Context, email, purpose, codeHash string) error {
	_, err := db.Pool.Exec(ctx, `
		insert into verification_codes (email, purpose, code_hash, created_at)
		values ($1, $2, $3, $4)
		on conflict (email, purpose) do update set code_hash = excluded.code_hash, created_at = excluded.created_at`,
		email, purpose, codeHash, time.Now().UTC())
	return err
}

// GetVerificationCode returns nil when there is no code waiting for that email and purpose.
func (db *Database) GetVerificationCode(ctx context.Context, email, purpose string) (*VerificationCode, error) {
	code := &VerificationCode{}
	err := db.Pool.QueryRow(ctx, `select code_hash, created_at from verification_codes where email = $1 and purpose = $2`, email, purpose).
		Scan(&code.CodeHash, &code.CreatedAt)
	if notFound(err) {
		return nil, nil
	}
	return code, err
}

func (db *Database) DeleteVerificationCode(ctx context.Context, email, purpose string) error {
	_, err := db.Pool.Exec(ctx, `delete from verification_codes where email = $1 and purpose = $2`, email, purpose)
	return err
}
