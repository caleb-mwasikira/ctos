package database

import (
	"context"
	"time"
)

// user_encryption_keys: one Secure Drop RSA key pair per user (see docs/secure-drop.md). The
// website's migrations own this table; this file only reads and writes it, the same as every
// other file in this package.

type EncryptionKey struct {
	PublicKey           string
	EncryptedPrivateKey string
	Fingerprint         string
	CreatedAt           time.Time
}

// GetEncryptionKey returns nil when the user has not set one up.
func (db *Database) GetEncryptionKey(ctx context.Context, userID int64) (*EncryptionKey, error) {
	key := &EncryptionKey{}
	err := db.Pool.QueryRow(ctx, `
		select public_key, encrypted_private_key, fingerprint, created_at
		from user_encryption_keys where user_id = $1`, userID).
		Scan(&key.PublicKey, &key.EncryptedPrivateKey, &key.Fingerprint, &key.CreatedAt)
	if notFound(err) {
		return nil, nil
	}
	return key, err
}

// SetEncryptionKey replaces whatever key pair the user already had, if any.
func (db *Database) SetEncryptionKey(ctx context.Context, userID int64, publicKey, encryptedPrivateKey, fingerprint string) error {
	now := time.Now().UTC()
	_, err := db.Pool.Exec(ctx, `
		insert into user_encryption_keys (user_id, public_key, encrypted_private_key, fingerprint, created_at, updated_at)
		values ($1, $2, $3, $4, $5, $5)
		on conflict (user_id) do update set
			public_key = excluded.public_key,
			encrypted_private_key = excluded.encrypted_private_key,
			fingerprint = excluded.fingerprint,
			updated_at = excluded.updated_at`,
		userID, publicKey, encryptedPrivateKey, fingerprint, now)
	return err
}

func (db *Database) DeleteEncryptionKey(ctx context.Context, userID int64) error {
	_, err := db.Pool.Exec(ctx, `delete from user_encryption_keys where user_id = $1`, userID)
	return err
}

// DepartmentMemberWithKey is a department member who has a Secure Drop key: enough to encrypt a
// file's AES key for them.
type DepartmentMemberWithKey struct {
	ID          int64
	Name        string
	PublicKey   string
	Fingerprint string
}

// GetDepartmentMembersWithKeys lists the department's members who have a Secure Drop key set up.
func (db *Database) GetDepartmentMembersWithKeys(ctx context.Context, departmentID int64) ([]DepartmentMemberWithKey, error) {
	rows, err := db.Pool.Query(ctx, `
		select users.id, users.name, user_encryption_keys.public_key, user_encryption_keys.fingerprint
		from department_user
		join users on users.id = department_user.user_id
		join departments on departments.id = department_user.department_id
		join user_encryption_keys on user_encryption_keys.user_id = users.id
		where department_user.department_id = $1 and department_user.locked_at is null
		  and not exists (select 1 from organization_locks
		                  where organization_locks.organization_id = departments.organization_id
		                    and organization_locks.user_id = users.id)
		order by lower(users.name), users.id`, departmentID)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	members := []DepartmentMemberWithKey{}
	for rows.Next() {
		var member DepartmentMemberWithKey
		if err := rows.Scan(&member.ID, &member.Name, &member.PublicKey, &member.Fingerprint); err != nil {
			return nil, err
		}
		members = append(members, member)
	}
	return members, rows.Err()
}
