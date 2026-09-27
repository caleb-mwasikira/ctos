package com.ctos.client

import com.ctos.client.data.groupByOrganization
import com.ctos.client.data.searchDepartments
import com.ctos.client.network.Department
import com.ctos.client.network.DepartmentRole
import com.ctos.client.network.OrganizationRef
import com.ctos.client.network.departmentRole
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OrganizationsTest {
    private val acme = OrganizationRef(1, "Acme Corp", "acme-corp")
    private val globex = OrganizationRef(3, "globex", "globex")

    private fun department(id: Long, name: String, organization: OrganizationRef, role: String = "member") =
        Department(id, name, name.lowercase().replace(' ', '-'), organization, role)

    private val departments = listOf(
        department(1, "Finance", acme),
        department(2, "Engineering", acme, "admin"),
        department(3, "Design Systems", globex, "lead"),
        department(4, "100% Remote", globex),
    )

    @Test
    fun departmentsAreGroupedByOrganizationSortedByName() {
        val organizations = groupByOrganization(departments)

        assertEquals(listOf("Acme Corp", "globex"), organizations.map { it.organization.name })
        assertEquals(listOf("Engineering", "Finance"), organizations[0].departments.map { it.name })
        assertEquals(listOf("100% Remote", "Design Systems"), organizations[1].departments.map { it.name })
    }

    @Test
    fun theHighestRoleInAnOrganizationIsShown() {
        val organizations = groupByOrganization(departments).associateBy { it.organization.slug }

        assertEquals(DepartmentRole.Admin, organizations.getValue("acme-corp").highestRole)
        assertEquals(DepartmentRole.Lead, organizations.getValue("globex").highestRole)
        assertTrue(organizations.getValue("globex").managesAnyDepartment)
    }

    @Test
    fun anOrganizationWithOnlyMemberRolesIsNotManaged() {
        val organizations = groupByOrganization(listOf(department(9, "Sales", acme)))

        assertEquals(DepartmentRole.Member, organizations.single().highestRole)
        assertTrue(!organizations.single().managesAnyDepartment)
    }

    @Test
    fun anUnknownRoleIsTreatedAsMember() {
        assertEquals(DepartmentRole.Member, department(1, "X", acme, "something-new").departmentRole)
    }

    @Test
    fun searchFindsDepartmentNamesAcrossOrganizationsIgnoringCase() {
        assertEquals(listOf("Engineering"), searchDepartments(departments, "ENG").map { it.name })
        // "e" is in a department of each organization
        assertEquals(
            listOf("100% Remote", "Design Systems", "Engineering", "Finance"),
            searchDepartments(departments, "e").map { it.name },
        )
    }

    @Test
    fun searchOnlyLooksAtDepartmentNames() {
        assertEquals(listOf("Engineering"), searchDepartments(departments, "engin").map { it.name })
        assertEquals(emptyList(), searchDepartments(departments, "acme"))
    }

    @Test
    fun searchTextIsMatchedLiterally() {
        assertEquals(listOf("100% Remote"), searchDepartments(departments, "%").map { it.name })
        assertEquals(emptyList(), searchDepartments(departments, "_"))
    }

    @Test
    fun anEmptyOrBlankSearchKeepsEverythingSortedByName() {
        assertEquals(listOf("100% Remote", "Design Systems", "Engineering", "Finance"), searchDepartments(departments, "  ").map { it.name })
    }
}
