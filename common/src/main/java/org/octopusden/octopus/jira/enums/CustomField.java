package org.octopusden.octopus.jira.enums;

import org.octopusden.octopus.releng.dto.Language;

import java.util.Objects;

public enum CustomField {

    HIGHLIGHT("Highlight"),
    RELEASE_HIGHLIGHTS("Release Highlights"),
    MANUALS_TO_BE_UPDATED("Manual(s) To Be Updated"),
    IMPACTS_ON("Impacts On"),
    CLIENT_ISSUE_IDT("Client Issue IDT"),
    DOCUMENTATION_STATUS("Documentation Status"),
    APPROVED_FOR_RELEASE("Versions Approved For Release"),
    PROOFREAD("Proofread"),
    PROOFREAD_HIGHLIGHTS("Highlights"),
    CLIENT_RELEASE_NOTES("Client Release Notes"),
    CLIENT_UPGRADE_NOTES("Client Upgrade Notes"),
    BUILDS("Builds"),
    PRODUCT("Product"),
    PRODUCT_LINE("Product Line"),
    EXPENSES_ITEM("Expenses Item"), //2DO - DEL
    EXPENSE_ITEM("Expense Item"), //
    CUSTOMER("Customer"),
    CUSTOMIZATION("Customization"),
    PA_DSS_IMPACT("PA DSS Impact"),
    PA_DSS_IMPACT_NOTES("PA DSS Impact Notes"),
    CHD_IMPACT("CHD Impact"),
    IMPACTS_REQUIREMENT("Impacts PADSS Requirement"),
    CRN_REQUIRED("CRN Required"),
    REQUIRED_ISSUES("Required Issues"),
    IPS_RELEASE("IPS Release"),
    SUB_COMPONENT_FIX_VERSIONS("SubComponent Fix Version/s"),
    RC_VERSIONS("RC Version/s"),
    RESOLUTION_DETAILS("Resolution Details"),
    TESTING_RESOLUTION("Testing Resolution"),
    CHECK_SCRIPT("Check Script"),
    LICENSE("License"),
    IPS_REQUIREMENT_REGION("IPS Requirement Region"),
    BITBUCKET_REPOSITORY("BitBucket Repository"),
    SOURCE_REPOSITORY("Source Repository"),
    CHANGE_APPROVED_BY("Change Approved by"),
    IMPACTS_SSF_REQUIREMENT("Impacts SSF Requirement"),
    SSF_IMPACT_NOTES("SSF Impact Notes"),
    TESTED_AGAINST_SSF_REQUIREMENTS("Tested against SSF requirements"),
    EFFECTIVE_DATE("Effective Date"),
    HOTFIX_TARGET_TYPE("Hotfix Target Type"),
    SYSTEM("System");

    private String name;

    CustomField(String customFieldName) {
        this.name = customFieldName;
    }

    public static CustomField getByName(String fieldName) {
        for (CustomField customField : CustomField.values()) {
            if (Objects.equals(customField.getName(), fieldName)) {
                return customField;
            }
        }
        return null;
    }

    public String getName(Language language) {
        String languageSuffix = Language.RU == language ? " (ru)" : "";
        return name + languageSuffix;
    }

    public String getName() {
        return getName(Language.EN);
    }
}
