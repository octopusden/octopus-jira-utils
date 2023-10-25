package org.octopusden.octopus.jira.enums;

import org.octopusden.octopus.releng.dto.Language;

import java.util.Objects;

public enum JiraCustomField {

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
    SYSTEM("System"),
    SPRINT("Sprint"),
    CLEAR_SPRINT_ON_RESOLVE("Clear Sprint on Resolve"),
    PDM("PDM"),
    DEVELOPERS("Developers"),
    APPLICATION_ARCHITECT("Application Architect"),
    SYSTEM_ARCHITECT("System Architect"),
    TESTERS("Testers"),
    DELIVERY_CONSULTANTS("Delivery Consultants"),
    TECHWRITERS("Techwriters"),
    TRANSLATORS("Translators"),
    NOTES_VERIFIERS("Notes Verifiers"),
    ACADEMY("Academy"),
    PRODUCT_MARKETING_MANAGER("Product Marketing Manager"),
    PRINCIPAL_PDM("Principal PDM"),
    TECH_LEAD("Tech Lead"),
    TEST_LEAD("Test Lead"),
    RELEASE_MANAGER("Release Manager"),
    START_SPRINT("Start Sprint"),
    END_SPRINT("End Sprint");

    private final String fieldName;

    JiraCustomField(String fieldName) {
        this.fieldName = fieldName;
    }

    public static JiraCustomField getByName(String fieldName) {
        for (JiraCustomField customField : JiraCustomField.values()) {
            if (Objects.equals(customField.getFieldName(), fieldName)) {
                return customField;
            }
        }
        return null;
    }

    /**
     @deprecated use {@link #getFieldName(Language language)} instead
     */
    @Deprecated
    public String getName(Language language) {
        return getFieldName(language);
    }

    /**
      @deprecated use {@link #getFieldName()} instead
     */
    @Deprecated
    public String getName() {
        return getFieldName();
    }

    public String getFieldName() {
        return getFieldName(Language.EN);
    }

    public String getFieldName(Language language) {
        String languageSuffix = Language.RU == language ? " (ru)" : "";
        return fieldName + languageSuffix;
    }
}
