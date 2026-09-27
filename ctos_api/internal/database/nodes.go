package database

import (
	"context"
	"errors"
	"strings"
	"time"

	"github.com/jackc/pgx/v5"
)

// nodes: the files and folders of a department, as the cloud holds them. A node is found by its
// path inside the department ("reports/summary.md"). A deleted node stays as a tombstone
// (DeletedAt set) so every device learns it is gone. node_changes is the log every change is
// written to: its ids only grow, so a device that remembers the last id it saw can ask for
// everything after it. Everything that changes a department's nodes runs under one lock per
// department, so two devices saving at once cannot leave the tree in a state neither meant.

const (
	NodeFile   = "file"
	NodeFolder = "folder"
)

type Node struct {
	ID           int64
	DepartmentID int64
	Path         string
	ParentPath   string
	Name         string
	Type         string
	Size         int64
	Checksum     *string
	Version      int64
	StorageKey   *string
	ModifiedAt   *time.Time
	DeletedAt    *time.Time
}

const nodeColumns = `nodes.id, nodes.department_id, nodes.path, nodes.parent_path, nodes.name, nodes.type,
	nodes.size, nodes.checksum, nodes.version, nodes.storage_path, nodes.modified_at, nodes.deleted_at`

func (node *Node) fields() []any {
	return []any{&node.ID, &node.DepartmentID, &node.Path, &node.ParentPath, &node.Name, &node.Type,
		&node.Size, &node.Checksum, &node.Version, &node.StorageKey, &node.ModifiedAt, &node.DeletedAt}
}

var (
	ErrNotFound     = errors.New("there is nothing at this path")
	ErrIsFolder     = errors.New("a folder is at this path")
	ErrIsFile       = errors.New("a file is at this path")
	ErrParentIsFile = errors.New("a file is where a folder is needed")
)

// ConflictError means the caller's idea of a node (its version) is out of date. Current is the
// node as it is now, or nil when it no longer exists.
type ConflictError struct {
	Current *Node
}

func (e *ConflictError) Error() string { return "the node changed since the version the caller knew" }

// ParentOf is the folder a path is in: "a/b/c.txt" -> "a/b", "c.txt" -> "".
func ParentOf(path string) string {
	index := strings.LastIndex(path, "/")
	if index < 0 {
		return ""
	}
	return path[:index]
}

func nameOf(path string) string {
	return path[strings.LastIndex(path, "/")+1:]
}

// runLocked runs [work] in a transaction that holds the department's lock until it ends.
func (db *Database) runLocked(ctx context.Context, departmentID int64, work func(tx pgx.Tx) error) error {
	tx, err := db.Pool.Begin(ctx)
	if err != nil {
		return err
	}
	defer tx.Rollback(ctx)

	if _, err := tx.Exec(ctx, `select pg_advisory_xact_lock(1001, $1::int)`, departmentID); err != nil {
		return err
	}
	if err := work(tx); err != nil {
		return err
	}
	return tx.Commit(ctx)
}

func liveNode(ctx context.Context, tx pgx.Tx, departmentID int64, path string) (*Node, error) {
	node := &Node{}
	err := tx.QueryRow(ctx, `select `+nodeColumns+` from nodes
		where department_id = $1 and path = $2 and deleted_at is null`, departmentID, path).Scan(node.fields()...)
	if notFound(err) {
		return nil, nil
	}
	return node, err
}

func logChange(ctx context.Context, tx pgx.Tx, departmentID int64, nodeID int64) (int64, error) {
	var changeID int64
	err := tx.QueryRow(ctx, `insert into node_changes (department_id, node_id, created_at)
		values ($1, $2, now()) returning id`, departmentID, nodeID).Scan(&changeID)
	return changeID, err
}

func insertNode(ctx context.Context, tx pgx.Tx, node *Node, userID int64, modifiedAt time.Time) (int64, error) {
	err := tx.QueryRow(ctx, `
		insert into nodes (department_id, path, parent_path, name, type, size, checksum, version, storage_path,
		                   created_by, updated_by, modified_at, created_at, updated_at)
		values ($1, $2, $3, $4, $5, $6, $7, 1, $8, $9, $9, $10, now(), now())
		returning `+nodeColumns,
		node.DepartmentID, node.Path, node.ParentPath, node.Name, node.Type, node.Size, node.Checksum, node.StorageKey,
		userID, modifiedAt).Scan(node.fields()...)
	if err != nil {
		return 0, err
	}
	return logChange(ctx, tx, node.DepartmentID, node.ID)
}

// ensureFolders makes sure every folder on the way to folderPath exists, creating the missing
// ones. It returns the last change it logged (0 when nothing was created).
func ensureFolders(ctx context.Context, tx pgx.Tx, departmentID int64, userID int64, folderPath string) (int64, error) {
	if folderPath == "" {
		return 0, nil
	}
	var lastChange int64
	built := ""
	for _, segment := range strings.Split(folderPath, "/") {
		if built != "" {
			built += "/"
		}
		built += segment

		existing, err := liveNode(ctx, tx, departmentID, built)
		if err != nil {
			return 0, err
		}
		if existing != nil {
			if existing.Type != NodeFolder {
				return 0, ErrParentIsFile
			}
			continue
		}
		folder := &Node{DepartmentID: departmentID, Path: built, ParentPath: ParentOf(built), Name: nameOf(built), Type: NodeFolder}
		if lastChange, err = insertNode(ctx, tx, folder, userID, time.Now().UTC()); err != nil {
			return 0, err
		}
	}
	return lastChange, nil
}

type SaveFileInput struct {
	DepartmentID int64
	UserID       int64
	Path         string
	// BaseVersion is the version of the file the caller changed: 0 to create a new file, the
	// version it last saw to replace one. Nil means the caller did not say.
	BaseVersion *int64
	Size        int64
	Checksum    string
	StorageKey  string
	ModifiedAt  time.Time
}

type SaveFileResult struct {
	Node     *Node
	ChangeID int64
	Created  bool
	// Unchanged is true when the file already had exactly this content: nothing was written.
	Unchanged bool
	// ReplacedKey is the content the new version replaced, to be deleted from disk.
	ReplacedKey *string
}

// SaveFile creates a file, or replaces its content with a new version. Missing parent folders
// are created. It returns a *ConflictError when the caller's base version is not the current one.
func (db *Database) SaveFile(ctx context.Context, in SaveFileInput) (*SaveFileResult, error) {
	result := &SaveFileResult{}
	err := db.runLocked(ctx, in.DepartmentID, func(tx pgx.Tx) error {
		existing, err := liveNode(ctx, tx, in.DepartmentID, in.Path)
		if err != nil {
			return err
		}

		if existing != nil {
			if existing.Type == NodeFolder {
				return ErrIsFolder
			}
			// Sending the same content again (a retry) changes nothing.
			if existing.Checksum != nil && *existing.Checksum == in.Checksum && existing.Size == in.Size {
				result.Node, result.Unchanged = existing, true
				return nil
			}
			if in.BaseVersion == nil || *in.BaseVersion != existing.Version {
				return &ConflictError{Current: existing}
			}

			node := &Node{}
			err := tx.QueryRow(ctx, `
				update nodes set size = $1, checksum = $2, storage_path = $3, version = version + 1,
				                 updated_by = $4, modified_at = $5, updated_at = now()
				where id = $6 returning `+nodeColumns,
				in.Size, in.Checksum, in.StorageKey, in.UserID, in.ModifiedAt, existing.ID).Scan(node.fields()...)
			if err != nil {
				return err
			}
			result.Node, result.ReplacedKey = node, existing.StorageKey
			result.ChangeID, err = logChange(ctx, tx, in.DepartmentID, node.ID)
			return err
		}

		// A new file. A caller that thought it was replacing one saw a version that is gone.
		if in.BaseVersion != nil && *in.BaseVersion != 0 {
			return &ConflictError{}
		}
		if _, err := ensureFolders(ctx, tx, in.DepartmentID, in.UserID, ParentOf(in.Path)); err != nil {
			return err
		}
		node := &Node{
			DepartmentID: in.DepartmentID, Path: in.Path, ParentPath: ParentOf(in.Path), Name: nameOf(in.Path),
			Type: NodeFile, Size: in.Size, Checksum: &in.Checksum, StorageKey: &in.StorageKey,
		}
		if result.ChangeID, err = insertNode(ctx, tx, node, in.UserID, in.ModifiedAt); err != nil {
			return err
		}
		result.Node, result.Created = node, true
		return nil
	})
	if err != nil {
		return nil, err
	}
	return result, nil
}

// CreateFolder makes a folder (and the folders above it). A folder that exists is returned as it is.
func (db *Database) CreateFolder(ctx context.Context, departmentID int64, userID int64, path string) (*Node, int64, error) {
	var folder *Node
	var changeID int64
	err := db.runLocked(ctx, departmentID, func(tx pgx.Tx) error {
		existing, err := liveNode(ctx, tx, departmentID, path)
		if err != nil {
			return err
		}
		if existing != nil {
			if existing.Type != NodeFolder {
				return ErrIsFile
			}
			folder = existing
			return nil
		}
		if _, err := ensureFolders(ctx, tx, departmentID, userID, ParentOf(path)); err != nil {
			return err
		}
		folder = &Node{DepartmentID: departmentID, Path: path, ParentPath: ParentOf(path), Name: nameOf(path), Type: NodeFolder}
		changeID, err = insertNode(ctx, tx, folder, userID, time.Now().UTC())
		return err
	})
	return folder, changeID, err
}

// DeleteNode deletes a file, or a folder with everything in it, leaving tombstones. It returns the
// storage keys of the files that were deleted, to be removed from disk, and the last change logged.
func (db *Database) DeleteNode(ctx context.Context, departmentID int64, userID int64, path string, baseVersion *int64) ([]string, int64, error) {
	var keys []string
	var changeID int64
	err := db.runLocked(ctx, departmentID, func(tx pgx.Tx) error {
		target, err := liveNode(ctx, tx, departmentID, path)
		if err != nil {
			return err
		}
		if target == nil {
			return ErrNotFound
		}
		if baseVersion != nil && *baseVersion != target.Version {
			return &ConflictError{Current: target}
		}

		doomed := []Node{*target}
		if target.Type == NodeFolder {
			rows, err := tx.Query(ctx, `select `+nodeColumns+` from nodes
				where department_id = $1 and deleted_at is null and starts_with(path, $2) order by path`,
				departmentID, path+"/")
			if err != nil {
				return err
			}
			for rows.Next() {
				var node Node
				if err := rows.Scan(node.fields()...); err != nil {
					rows.Close()
					return err
				}
				doomed = append(doomed, node)
			}
			rows.Close()
			if err := rows.Err(); err != nil {
				return err
			}
		}

		for _, node := range doomed {
			if _, err := tx.Exec(ctx, `update nodes set deleted_at = now(), version = version + 1, updated_by = $1, updated_at = now()
				where id = $2`, userID, node.ID); err != nil {
				return err
			}
			if changeID, err = logChange(ctx, tx, departmentID, node.ID); err != nil {
				return err
			}
			if node.StorageKey != nil {
				keys = append(keys, *node.StorageKey)
			}
		}
		return nil
	})
	return keys, changeID, err
}

// GetLiveNode returns the node at a path, or nil when there is none (or it was deleted).
func (db *Database) GetLiveNode(ctx context.Context, departmentID int64, path string) (*Node, error) {
	node := &Node{}
	err := db.Pool.QueryRow(ctx, `select `+nodeColumns+` from nodes
		where department_id = $1 and path = $2 and deleted_at is null`, departmentID, path).Scan(node.fields()...)
	if notFound(err) {
		return nil, nil
	}
	return node, err
}

type ChangedNode struct {
	Node
	ChangeID int64
}

// GetChanges lists what changed in a department after a cursor: each node once, as it is now
// (a deleted node comes back as a tombstone), oldest change first, at most limit of them. cursor
// is what the caller should send next time; more is true when there is more to fetch right now.
func (db *Database) GetChanges(ctx context.Context, departmentID int64, after int64, limit int) (nodes []ChangedNode, cursor int64, more bool, err error) {
	// One snapshot for both queries, so a change made in between cannot be skipped.
	tx, err := db.Pool.BeginTx(ctx, pgx.TxOptions{IsoLevel: pgx.RepeatableRead, AccessMode: pgx.ReadOnly})
	if err != nil {
		return nil, 0, false, err
	}
	defer tx.Rollback(ctx)

	rows, err := tx.Query(ctx, `
		with latest as (
			select node_id, max(id) as change_id from node_changes
			where department_id = $1 and id > $2
			group by node_id order by max(id) limit $3
		)
		select `+nodeColumns+`, latest.change_id from latest
		join nodes on nodes.id = latest.node_id order by latest.change_id`, departmentID, after, limit)
	if err != nil {
		return nil, 0, false, err
	}
	nodes = []ChangedNode{}
	for rows.Next() {
		var changed ChangedNode
		if err := rows.Scan(append(changed.Node.fields(), &changed.ChangeID)...); err != nil {
			rows.Close()
			return nil, 0, false, err
		}
		nodes = append(nodes, changed)
	}
	rows.Close()
	if err := rows.Err(); err != nil {
		return nil, 0, false, err
	}

	if len(nodes) == limit {
		return nodes, nodes[len(nodes)-1].ChangeID, true, nil
	}
	err = tx.QueryRow(ctx, `select coalesce(max(id), 0) from node_changes where department_id = $1`, departmentID).Scan(&cursor)
	return nodes, max(cursor, after), false, err
}
