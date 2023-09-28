package org.octopusden.octopus.jira.utils;

import org.octopusden.octopus.jira.enums.JiraCustomField;
import org.octopusden.octopus.releng.dto.Language;
import org.junit.Assert;
import org.junit.Test;

public class TestCustomFieldEnum {
    @Test
    public void testDifferentLanguage() {
        Assert.assertEquals("Highlight", JiraCustomField.HIGHLIGHT.getName(Language.EN));
        Assert.assertEquals("Highlight (ru)", JiraCustomField.HIGHLIGHT.getName(Language.RU));
    }
}
