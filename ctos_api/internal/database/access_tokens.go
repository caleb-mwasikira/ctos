package database

import (
	"context"
	"time"
)

// personal_access_tokens: the login tokens (Laravel Sanctum's table).
// Only the sha256 hash of a token is stored.

// GetUserByAccessToken finds the user a token belongs to and returns the
// token's id as well. tokenID 0 means the client did not send the "id|" prefix.
// It returns nil when the token is unknown or expired.
func (db *Database) GetUserByAccessToken(ctx context.Context, tokenID int64, tokenHash string) (*User, int64, error) {
	user := &User{}
	var foundTokenID int64
	err := db.Pool.QueryRow(ctx, `
		select personal_access_tokens.id, users.id, users.name, users.email, users.password, users.email_verified_at is not null
		from personal_access_tokens
		join users on users.id = personal_access_tokens.tokenable_id
		where personal_access_tokens.tokenable_type = 'App\Models\User'
		  and personal_access_tokens.token = $1
		  and ($2 = 0 or personal_access_tokens.id = $2)
		  and (personal_access_tokens.expires_at is null or personal_access_tokens.expires_at > $3)`,
		tokenHash, tokenID, time.Now().UTC(),
	).Scan(append([]any{&foundTokenID}, user.fields()...)...)
	if notFound(err) {
		return nil, 0, nil
	}
	return user, foundTokenID, err
}

func (db *Database) CreateAccessToken(ctx context.Context, userID int64, deviceName string, tokenHash string) (int64, error) {
	now := time.Now().UTC()
	var tokenID int64
	err := db.Pool.QueryRow(ctx, `
		insert into personal_access_tokens (tokenable_type, tokenable_id, name, token, abilities, created_at, updated_at)
		values ('App\Models\User', $1, $2, $3, '["*"]', $4, $4)
		returning id`, userID, deviceName, tokenHash, now).Scan(&tokenID)
	return tokenID, err
}

func (db *Database) MarkAccessTokenUsed(ctx context.Context, tokenID int64) error {
	_, err := db.Pool.Exec(ctx, `update personal_access_tokens set last_used_at = $1 where id = $2`, time.Now().UTC(), tokenID)
	return err
}

func (db *Database) DeleteAccessToken(ctx context.Context, tokenID int64) error {
	_, err := db.Pool.Exec(ctx, `delete from personal_access_tokens where id = $1`, tokenID)
	return err
}

// DeleteAccessTokensForUser signs every device out, the same way the website's own password
// reset does ($user->tokens()->delete()).
func (db *Database) DeleteAccessTokensForUser(ctx context.Context, userID int64) error {
	_, err := db.Pool.Exec(ctx, `delete from personal_access_tokens where tokenable_type = 'App\Models\User' and tokenable_id = $1`, userID)
	return err
}
