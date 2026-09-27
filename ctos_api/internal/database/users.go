package database

import (
	"context"
	"time"
)

type User struct {
	ID           int64
	Name         string
	Email        string
	PasswordHash string
	// EmailVerified is false until the user opens the link the website emailed them.
	EmailVerified bool
}

const userColumns = `id, name, email, password, email_verified_at is not null`

func (user *User) fields() []any {
	return []any{&user.ID, &user.Name, &user.Email, &user.PasswordHash, &user.EmailVerified}
}

// GetUserByEmail returns nil when nobody has that email. The website stores
// emails in lower case.
func (db *Database) GetUserByEmail(ctx context.Context, email string) (*User, error) {
	user := &User{}
	err := db.Pool.QueryRow(ctx, `select `+userColumns+` from users where email = $1`, email).Scan(user.fields()...)
	if notFound(err) {
		return nil, nil
	}
	return user, err
}

// CreateUser makes a new account, unverified, the same shape the website's own sign-up form
// makes. The caller has already hashed the password.
func (db *Database) CreateUser(ctx context.Context, name, email, passwordHash string) (int64, error) {
	now := time.Now().UTC()
	var id int64
	err := db.Pool.QueryRow(ctx, `
		insert into users (name, email, password, created_at, updated_at)
		values ($1, $2, $3, $4, $4)
		returning id`, name, email, passwordHash, now).Scan(&id)
	return id, err
}

// MarkEmailVerified is what opening the emailed link does on the website; entering the right
// code does the same thing from the app.
func (db *Database) MarkEmailVerified(ctx context.Context, userID int64) error {
	now := time.Now().UTC()
	_, err := db.Pool.Exec(ctx, `update users set email_verified_at = $1, updated_at = $1 where id = $2`, now, userID)
	return err
}

// SetPassword replaces a user's password hash, e.g. after a reset.
func (db *Database) SetPassword(ctx context.Context, userID int64, passwordHash string) error {
	_, err := db.Pool.Exec(ctx, `update users set password = $1, updated_at = $2 where id = $3`, passwordHash, time.Now().UTC(), userID)
	return err
}
