package org.octopusden.octopus.jira.enums;

public enum IssueTypeEnum {

    RELEASE_REQUEST("Release Request"),
    IPS_BULLETIN("IPS Bulletin"),
    IPS_RELEASE("IPS Release"),
    IPS_REQUIREMENT("IPS Requirement"),
    BACKPORTING("Backporting"),
    BUG("Bug"),
    NEW_FEATURE("New Feature"),
    TASK("Task"),
    MANDATORY_UPDATE("Mandatory Update"),
    ENHANCEMENT("Enhancement"),
    HOTFIX("Hotfix"),
    DOCUMENTATION("Documentation"),
    EPIC("Epic"),
    PRODUCT_CARD("Product Card"),
    SPRINT("Sprint");

    private final String name;

    IssueTypeEnum(String issueTypeName) {
        this.name = issueTypeName;
    }

    public String getName() {
        return name;
    }
}
