package org.octopusden.octopus.jira.utils;

import org.octopusden.octopus.jira.enums.CustomField;
import org.octopusden.octopus.releng.dto.Language;
import org.junit.Assert;
import org.junit.Test;

public class TestCustomFieldEnum {
    @Test
    public void testDifferentLanguage() {
        Assert.assertEquals("Highlight", CustomField.HIGHLIGHT.getName(Language.EN));
        Assert.assertEquals("Highlight (ru)", CustomField.HIGHLIGHT.getName(Language.RU));
    }
}
