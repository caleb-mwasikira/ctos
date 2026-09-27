// Package database holds every SQL query of the API. There is one file per
// table (users.go, departments.go, ...) and each function does one simple
// thing: GetDepartmentsOfUser, AddDepartmentMember...
//
// The tables belong to the Laravel website: its migrations create and change
// them, this API only reads and writes rows. Never add a table here.
//
// Conventions:
//   - GetX returns one row (nil when there is none) and GetXs returns a list.
//   - A function that needs several statements to stay consistent runs them
//     in a transaction inside that function.
//   - Timestamps are written with time.Now().UTC(), like the Laravel app does.
package database

import (
	"context"
	"errors"

	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"
)

type Database struct {
	Pool *pgxpool.Pool
}

func Connect(ctx context.Context, databaseURL string) (*Database, error) {
	pool, err := pgxpool.New(ctx, databaseURL)
	if err != nil {
		return nil, err
	}
	if err := pool.Ping(ctx); err != nil {
		return nil, err
	}
	return &Database{Pool: pool}, nil
}

func (db *Database) Close() {
	db.Pool.Close()
}

// notFound is true when a query that should return one row returned none.
func notFound(err error) bool {
	return errors.Is(err, pgx.ErrNoRows)
}
