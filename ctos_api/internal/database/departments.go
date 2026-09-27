package database

import (
	"context"
	"time"
)

// A department as a user sees it: the department, its organization, and the
// role the user has in it ("admin" or "member").
type MemberDepartment struct {
	ID               int64
	Name             string
	Slug             string
	OrganizationID   int64
	OrganizationName string
	OrganizationSlug string
	Role             string
}

const memberDepartmentColumns = `departments.id, departments.name, departments.slug,
	organizations.id, organizations.name, organizations.slug, department_user.role`

const memberDepartmentFrom = `from department_user
	join departments on departments.id = department_user.department_id
	join organizations on organizations.id = departments.organization_id`

// activeMembership is what gives a member access: the membership is not locked, and the
// organization has not locked the user out. Every query for "the user's departments" uses it.
const activeMembership = `department_user.locked_at is null
	and not exists (select 1 from organization_locks
		where organization_locks.organization_id = organizations.id
		  and organization_locks.user_id = department_user.user_id)`

func (department *MemberDepartment) fields() []any {
	return []any{&department.ID, &department.Name, &department.Slug,
		&department.OrganizationID, &department.OrganizationName, &department.OrganizationSlug, &department.Role}
}

// GetDepartmentsOfUser lists the departments the user is a member of.
func (db *Database) GetDepartmentsOfUser(ctx context.Context, userID int64) ([]MemberDepartment, error) {
	rows, err := db.Pool.Query(ctx, `select `+memberDepartmentColumns+` `+memberDepartmentFrom+`
		where department_user.user_id = $1 and `+activeMembership+`
		order by departments.name, departments.id`, userID)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	departments := []MemberDepartment{}
	for rows.Next() {
		var department MemberDepartment
		if err := rows.Scan(department.fields()...); err != nil {
			return nil, err
		}
		departments = append(departments, department)
	}
	return departments, rows.Err()
}

// GetDepartmentOfUser returns nil when the user is not a member of the department.
func (db *Database) GetDepartmentOfUser(ctx context.Context, userID int64, departmentID int64) (*MemberDepartment, error) {
	department := &MemberDepartment{}
	err := db.Pool.QueryRow(ctx, `select `+memberDepartmentColumns+` `+memberDepartmentFrom+`
		where department_user.user_id = $1 and departments.id = $2 and `+activeMembership+``, userID, departmentID).Scan(department.fields()...)
	if notFound(err) {
		return nil, nil
	}
	return department, err
}

// A department found by the ID people type when they join it.
type JoinableDepartment struct {
	ID             int64
	AccessCodeHash string // bcrypt; the plain code is never stored
}

// GetDepartmentBySlug returns nil when there is no department with that ID. A
// department's slug is its ID: it is unique across every organization.
func (db *Database) GetDepartmentBySlug(ctx context.Context, slug string) (*JoinableDepartment, error) {
	department := &JoinableDepartment{}
	err := db.Pool.QueryRow(ctx, `select id, access_code from departments where slug = $1`, slug).
		Scan(&department.ID, &department.AccessCodeHash)
	if notFound(err) {
		return nil, nil
	}
	return department, err
}

// IsLockedOutOfDepartment says whether the owner locked the user out of the department, or of
// the whole organization it belongs to.
func (db *Database) IsLockedOutOfDepartment(ctx context.Context, userID int64, departmentID int64) (bool, error) {
	var locked bool
	err := db.Pool.QueryRow(ctx, `
		select exists(select 1 from department_user
		              where department_id = $1 and user_id = $2 and locked_at is not null)
		    or exists(select 1 from organization_locks
		              join departments on departments.organization_id = organization_locks.organization_id
		              where departments.id = $1 and organization_locks.user_id = $2)`,
		departmentID, userID).Scan(&locked)
	return locked, err
}

// AddDepartmentMember makes the user a plain member. A user who is already
// in the department keeps their role (an admin is not turned into a member).
func (db *Database) AddDepartmentMember(ctx context.Context, departmentID int64, userID int64) error {
	now := time.Now().UTC()
	_, err := db.Pool.Exec(ctx, `
		insert into department_user (department_id, user_id, role, created_at, updated_at)
		values ($1, $2, 'member', $3, $3)
		on conflict (department_id, user_id) do nothing`, departmentID, userID, now)
	return err
}

// GetDepartmentOfUserBySlug returns nil when the user is not a member of the department (or is
// locked out of it), and so has no business knowing it exists.
func (db *Database) GetDepartmentOfUserBySlug(ctx context.Context, userID int64, slug string) (*MemberDepartment, error) {
	department := &MemberDepartment{}
	err := db.Pool.QueryRow(ctx, `select `+memberDepartmentColumns+` `+memberDepartmentFrom+`
		where department_user.user_id = $1 and departments.slug = $2 and `+activeMembership, userID, slug).Scan(department.fields()...)
	if notFound(err) {
		return nil, nil
	}
	return department, err
}

// IsOrganizationOwner says whether the user owns the organization.
func (db *Database) IsOrganizationOwner(ctx context.Context, userID int64, organizationID int64) (bool, error) {
	var owner bool
	err := db.Pool.QueryRow(ctx, `select exists(select 1 from organizations where id = $1 and owner_id = $2)`,
		organizationID, userID).Scan(&owner)
	return owner, err
}

type DepartmentMember struct {
	ID    int64
	Name  string
	Email string
	Role  string
}

// GetDepartmentMembers lists the people who can currently open the department: members that are
// not locked, in an organization that has not locked them out.
func (db *Database) GetDepartmentMembers(ctx context.Context, departmentID int64) ([]DepartmentMember, error) {
	rows, err := db.Pool.Query(ctx, `
		select users.id, users.name, users.email, department_user.role
		from department_user
		join users on users.id = department_user.user_id
		join departments on departments.id = department_user.department_id
		where department_user.department_id = $1 and department_user.locked_at is null
		  and not exists (select 1 from organization_locks
		                  where organization_locks.organization_id = departments.organization_id
		                    and organization_locks.user_id = users.id)
		order by lower(users.name), users.id`, departmentID)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	members := []DepartmentMember{}
	for rows.Next() {
		var member DepartmentMember
		if err := rows.Scan(&member.ID, &member.Name, &member.Email, &member.Role); err != nil {
			return nil, err
		}
		members = append(members, member)
	}
	return members, rows.Err()
}

// GetEncryptedAccessCode returns the copy of the access code the website keeps for admins to
// show, encrypted with its APP_KEY. It is nil for a department made before that copy existed.
func (db *Database) GetEncryptedAccessCode(ctx context.Context, departmentID int64) (*string, error) {
	var encrypted *string
	err := db.Pool.QueryRow(ctx, `select access_code_encrypted from departments where id = $1`, departmentID).Scan(&encrypted)
	return encrypted, err
}

// SetAccessCode overwrites a department's access code: hash is what joining checks against next
// (bcrypt, matching Department::acceptsAccessCode); encrypted is the copy managers can be shown
// (Laravel's Crypt format, or nil to leave it unset β€” see accessCodeOf in handlers/departments.go).
func (db *Database) SetAccessCode(ctx context.Context, departmentID int64, hash string, encrypted *string) error {
	_, err := db.Pool.Exec(ctx, `update departments set access_code = $1, access_code_encrypted = $2, updated_at = now() where id = $3`,
		hash, encrypted, departmentID)
	return err
}

type DepartmentInvitation struct {
	ID    int64
	Email string
}

// GetDepartmentInvitations lists the email addresses the department was shared with that have not
// signed up and confirmed the address yet.
func (db *Database) GetDepartmentInvitations(ctx context.Context, departmentID int64) ([]DepartmentInvitation, error) {
	rows, err := db.Pool.Query(ctx, `select id, email from department_invitations where department_id = $1 order by email`, departmentID)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	invitations := []DepartmentInvitation{}
	for rows.Next() {
		var invitation DepartmentInvitation
		if err := rows.Scan(&invitation.ID, &invitation.Email); err != nil {
			return nil, err
		}
		invitations = append(invitations, invitation)
	}
	return invitations, rows.Err()
}

// LeaveDepartment removes the user's membership.
func (db *Database) LeaveDepartment(ctx context.Context, userID int64, departmentID int64) error {
	_, err := db.Pool.Exec(ctx, `delete from department_user where department_id = $1 and user_id = $2`, departmentID, userID)
	return err
}

// ShareCandidate is who a department is being shared with, when that email already has an account.
type ShareCandidate struct {
	ID       int64
	Name     string
	Verified bool
}

// FindUserByEmail looks a user up by email, the same case-insensitive way the website does. nil
// means no account has that address.
func (db *Database) FindUserByEmail(ctx context.Context, email string) (*ShareCandidate, error) {
	candidate := &ShareCandidate{}
	err := db.Pool.QueryRow(ctx, `select id, name, email_verified_at is not null from users where lower(email) = lower($1)`, email).
		Scan(&candidate.ID, &candidate.Name, &candidate.Verified)
	if notFound(err) {
		return nil, nil
	}
	return candidate, err
}

// IsMemberOfDepartment says whether the user has any membership row for the department at all,
// locked or not. Unlike the "active membership" check used to decide access, sharing must never
// offer a spot to someone who already has one, even a locked one.
func (db *Database) IsMemberOfDepartment(ctx context.Context, departmentID int64, userID int64) (bool, error) {
	var member bool
	err := db.Pool.QueryRow(ctx, `select exists(select 1 from department_user where department_id = $1 and user_id = $2)`,
		departmentID, userID).Scan(&member)
	return member, err
}

// ShareDepartment gives someone access to a department: an existing, verified account is added as
// a member straight away; anyone else gets a pending invitation that becomes a membership once
// they sign up and confirm that address (see the website's AcceptDepartmentInvitations listener).
// This matches ShareDepartmentAction on the website, so sharing from either place behaves the same.
//
// sendEmail is called once the row is written, still inside the same transaction: if it fails,
// nothing is kept, the same as the website (which sends the email inside its own DB transaction).
// added says whether an existing account was attached rather than invited.
func (db *Database) ShareDepartment(ctx context.Context, departmentID int64, invitedBy int64, candidate *ShareCandidate, email string, sendEmail func() error) (added bool, err error) {
	tx, err := db.Pool.Begin(ctx)
	if err != nil {
		return false, err
	}
	defer tx.Rollback(ctx)

	now := time.Now().UTC()
	if candidate != nil && candidate.Verified {
		if _, err := tx.Exec(ctx, `
			insert into department_user (department_id, user_id, role, created_at, updated_at)
			values ($1, $2, 'member', $3, $3)
			on conflict (department_id, user_id) do nothing`, departmentID, candidate.ID, now); err != nil {
			return false, err
		}
		added = true
	} else {
		if _, err := tx.Exec(ctx, `
			insert into department_invitations (department_id, email, invited_by, created_at, updated_at)
			values ($1, $2, $3, $4, $4)
			on conflict (department_id, email) do nothing`, departmentID, email, invitedBy, now); err != nil {
			return false, err
		}
	}

	if err := sendEmail(); err != nil {
		return false, err
	}
	return added, tx.Commit(ctx)
}

// DeleteInvitation withdraws a pending invitation. found is false when there was no such
// invitation for that department (already accepted, already withdrawn, or never existed).
func (db *Database) DeleteInvitation(ctx context.Context, departmentID int64, invitationID int64) (email string, found bool, err error) {
	err = db.Pool.QueryRow(ctx, `delete from department_invitations where id = $1 and department_id = $2 returning email`,
		invitationID, departmentID).Scan(&email)
	if notFound(err) {
		return "", false, nil
	}
	return email, err == nil, err
}
