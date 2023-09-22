package org.octopusden.octopus.jira.enums

class CustomFieldTest extends GroovyTestCase {
    void testGetByName() {
        assert CustomField.CLIENT_RELEASE_NOTES == CustomField.getByName("Client Release Notes")
        assertNull(CustomField.getByName("NOT_EXISTING_FIELD"))
    }
}
