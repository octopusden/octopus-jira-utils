package org.octopusden.octopus.jira.enums

class CustomFieldTest extends GroovyTestCase {
    void testGetByName() {
        assert JiraCustomField.CLIENT_RELEASE_NOTES == JiraCustomField.getByName("Client Release Notes")
        assertNull(JiraCustomField.getByName("NOT_EXISTING_FIELD"))
    }
}
