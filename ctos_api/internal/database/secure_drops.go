package database

import (
	"context"
	"time"
)

// secure_drops / secure_drop_recipients: end-to-end encrypted files between department members
// (see docs/secure-drop.md). The website's migrations own both tables.

type SecureDrop struct {
	ID             int64
	DepartmentID   int64
	DepartmentName string
	OwnerID        int64
	OwnerName      string
	Name           string
	Size           int64
	StoragePath    string
	IV             string
	CreatedAt      time.Time
}

type SecureDropRecipientInput struct {
	UserID       int64
	EncryptedKey string
}

// CreateSecureDrop stores a drop and every recipient's wrapped copy of its key in one
// transaction: either everyone who should be able to open it can, or nothing is created.
func (db *Database) CreateSecureDrop(ctx context.Context, departmentID, ownerID int64, name string, size int64, storagePath, iv string, recipients []SecureDropRecipientInput) (int64, error) {
	tx, err := db.Pool.Begin(ctx)
	if err != nil {
		return 0, err
	}
	defer tx.Rollback(ctx)

	now := time.Now().UTC()
	var dropID int64
	err = tx.QueryRow(ctx, `
		insert into secure_drops (department_id, owner_id, name, size, storage_path, iv, created_at, updated_at)
		values ($1, $2, $3, $4, $5, $6, $7, $7)
		returning id`, departmentID, ownerID, name, size, storagePath, iv, now).Scan(&dropID)
	if err != nil {
		return 0, err
	}

	for _, recipient := range recipients {
		if _, err := tx.Exec(ctx, `
			insert into secure_drop_recipients (secure_drop_id, user_id, encrypted_key, created_at, updated_at)
			values ($1, $2, $3, $4, $4)`, dropID, recipient.UserID, recipient.EncryptedKey, now); err != nil {
			return 0, err
		}
	}

	return dropID, tx.Commit(ctx)
}

const secureDropColumns = `secure_drops.id, secure_drops.department_id, departments.name,
	secure_drops.owner_id, users.name, secure_drops.name, secure_drops.size,
	secure_drops.storage_path, secure_drops.iv, secure_drops.created_at`

const secureDropFrom = `from secure_drops
	join departments on departments.id = secure_drops.department_id
	join users on users.id = secure_drops.owner_id`

func scanSecureDrop(row interface{ Scan(...any) error }) (*SecureDrop, error) {
	drop := &SecureDrop{}
	err := row.Scan(&drop.ID, &drop.DepartmentID, &drop.DepartmentName, &drop.OwnerID, &drop.OwnerName,
		&drop.Name, &drop.Size, &drop.StoragePath, &drop.IV, &drop.CreatedAt)
	if notFound(err) {
		return nil, nil
	}
	return drop, err
}

func (db *Database) GetSecureDrop(ctx context.Context, id int64) (*SecureDrop, error) {
	return scanSecureDrop(db.Pool.QueryRow(ctx, `select `+secureDropColumns+` `+secureDropFrom+` where secure_drops.id = $1`, id))
}

// GetSecureDropsSent lists what a user has sent, most recent first.
func (db *Database) GetSecureDropsSent(ctx context.Context, userID int64) ([]SecureDrop, error) {
	return queryDrops(ctx, db, `select `+secureDropColumns+` `+secureDropFrom+`
		where secure_drops.owner_id = $1 order by secure_drops.created_at desc`, userID)
}

// GetSecureDropsReceived lists what was sent to a user (not counting their own uploads), most recent first.
func (db *Database) GetSecureDropsReceived(ctx context.Context, userID int64) ([]SecureDrop, error) {
	return queryDrops(ctx, db, `select `+secureDropColumns+` `+secureDropFrom+`
		join secure_drop_recipients on secure_drop_recipients.secure_drop_id = secure_drops.id
		where secure_drop_recipients.user_id = $1 and secure_drops.owner_id != $1
		order by secure_drops.created_at desc`, userID)
}

func queryDrops(ctx context.Context, db *Database, sql string, args ...any) ([]SecureDrop, error) {
	rows, err := db.Pool.Query(ctx, sql, args...)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	drops := []SecureDrop{}
	for rows.Next() {
		drop, err := scanSecureDrop(rows)
		if err != nil {
			return nil, err
		}
		drops = append(drops, *drop)
	}
	return drops, rows.Err()
}

func (db *Database) DeleteSecureDrop(ctx context.Context, id int64) error {
	_, err := db.Pool.Exec(ctx, `delete from secure_drops where id = $1`, id)
	return err
}

// GetSecureDropRecipientKey returns nil when the user is not the owner or a recipient of the drop.
func (db *Database) GetSecureDropRecipientKey(ctx context.Context, dropID, userID int64) (*string, error) {
	var encryptedKey string
	err := db.Pool.QueryRow(ctx, `
		select encrypted_key from secure_drop_recipients where secure_drop_id = $1 and user_id = $2`,
		dropID, userID).Scan(&encryptedKey)
	if notFound(err) {
		return nil, nil
	}
	return &encryptedKey, err
}

// DeleteSecureDropRecipientRowsFor removes a user's own copy of every drop sent to them (used
// when they remove their key pair: their old wrapped key is useless without it).
func (db *Database) DeleteSecureDropRecipientRowsFor(ctx context.Context, userID int64) error {
	_, err := db.Pool.Exec(ctx, `delete from secure_drop_recipients where user_id = $1`, userID)
	return err
}

func (db *Database) MarkSecureDropOpened(ctx context.Context, dropID, userID int64) error {
	_, err := db.Pool.Exec(ctx, `
		update secure_drop_recipients set opened_at = $1, updated_at = $1
		where secure_drop_id = $2 and user_id = $3`, time.Now().UTC(), dropID, userID)
	return err
}
