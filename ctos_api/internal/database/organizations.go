package database

import (
	"context"
	"crypto/rand"
	"fmt"
	"math/big"
	"strings"
	"time"
)

// An organization as its owner or a member sees it: enough to show it on the dashboard even
// before it has any departments.
type OwnedOrganization struct {
	ID              int64
	Name            string
	Slug            string
	DepartmentCount int64
	IsOwner         bool
}

// GetOrganizationsOfUser lists organizations the user owns, or belongs to through an unlocked
// department membership. Matches Organization::visibleTo on the website, so this is the same set
// of organizations the website's dashboard would show.
func (db *Database) GetOrganizationsOfUser(ctx context.Context, userID int64) ([]OwnedOrganization, error) {
	rows, err := db.Pool.Query(ctx, `
		select organizations.id, organizations.name, organizations.slug, organizations.owner_id = $1,
		       (select count(*) from departments where departments.organization_id = organizations.id)
		from organizations
		where organizations.owner_id = $1
		   or exists (
		     select 1 from department_user
		     join departments on departments.id = department_user.department_id
		     where departments.organization_id = organizations.id and department_user.user_id = $1 and `+activeMembership+`
		   )
		order by lower(organizations.name), organizations.id`, userID)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	organizations := []OwnedOrganization{}
	for rows.Next() {
		var organization OwnedOrganization
		if err := rows.Scan(&organization.ID, &organization.Name, &organization.Slug, &organization.IsOwner, &organization.DepartmentCount); err != nil {
			return nil, err
		}
		organizations = append(organizations, organization)
	}
	return organizations, rows.Err()
}

// CreateOrganization makes a new organization owned by the user, with a slug unique across every
// organization (built from the name the same way Organization::uniqueSlugFor does on the website:
// the name slugified, and a random suffix added if that is already taken).
func (db *Database) CreateOrganization(ctx context.Context, ownerID int64, name string) (*OwnedOrganization, error) {
	base := slugify(name)
	slug := base
	now := time.Now().UTC()

	for attempt := 0; attempt < 20; attempt++ {
		var id int64
		err := db.Pool.QueryRow(ctx, `
			insert into organizations (name, slug, owner_id, created_at, updated_at)
			values ($1, $2, $3, $4, $4)
			on conflict (slug) do nothing
			returning id`, name, slug, ownerID, now).Scan(&id)
		switch {
		case err == nil:
			return &OwnedOrganization{ID: id, Name: name, Slug: slug, DepartmentCount: 0, IsOwner: true}, nil
		case notFound(err):
			slug = base + "-" + randomSlugSuffix()
		default:
			return nil, err
		}
	}
	return nil, fmt.Errorf("could not find a free slug for organization %q", name)
}

// slugify turns a name into a URL-safe slug the same way the website's Str::slug does for the
// plain ASCII case this needs: lower-case letters and digits, runs of anything else become one
// "-", and leading/trailing "-" are trimmed. An empty result falls back to "organization".
func slugify(name string) string {
	var b strings.Builder
	lastWasDash := true // true at the start too, so a leading run of separators is dropped
	for _, r := range strings.ToLower(name) {
		if r >= 'a' && r <= 'z' || r >= '0' && r <= '9' {
			b.WriteRune(r)
			lastWasDash = false
		} else if !lastWasDash {
			b.WriteByte('-')
			lastWasDash = true
		}
	}
	slug := strings.TrimRight(b.String(), "-")
	if slug == "" {
		return "organization"
	}
	return slug
}

func randomSlugSuffix() string {
	const alphabet = "abcdefghijklmnopqrstuvwxyz0123456789"
	suffix := make([]byte, 4)
	for i := range suffix {
		n, _ := rand.Int(rand.Reader, big.NewInt(int64(len(alphabet))))
		suffix[i] = alphabet[n.Int64()]
	}
	return string(suffix)
}
