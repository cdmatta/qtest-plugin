/**
 * 
 */
package com.qasymphony.ci.plugin.parse;

import hudson.tasks.junit.CaseResult;
import hudson.tasks.junit.SuiteResult;
import hudson.tasks.junit.TestResult;
import hudson.tasks.junit.CaseResult.Status;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.commons.codec.binary.Base64;

import com.qasymphony.ci.plugin.model.AutomationAttachment;
import com.qasymphony.ci.plugin.model.AutomationTestLog;
import com.qasymphony.ci.plugin.model.AutomationTestResult;

/**
 * @author anpham
 *
 */
public class CommonParsingUtils {
  public static final Integer LIMIT_TXT_FILES = 5;
  public static final String EXT_TEXT_FILE = ".txt";
  public static final String EXT_ZIP_FILE = ".zip";
  /**
   * 
   * @param testResults
   * @param startTime
   * @return
   */
  public static List<AutomationTestResult> toAutomationTestResults(List<TestResult> testResults, Date startTime, Date completedTime) throws Exception{
    HashMap<String, AutomationTestResult> automationTestResultMap = new HashMap<>();

    AutomationTestResult automationTestResult = null;
    AutomationTestLog automationTestLog = null;
    
    for(TestResult testResult: testResults){
      for (SuiteResult suite : testResult.getSuites()) {
        if (suite.getCases() == null) {
          continue;
        } else {
          for (CaseResult caseResult : suite.getCases()) {
            if (automationTestResultMap.containsKey(caseResult.getClassName())) {
              automationTestResult = automationTestResultMap.get(caseResult.getClassName());
              if (caseResult.isFailed()) {
                automationTestResult.setStatus(Status.FAILED.toString());
              }
            } else {
              automationTestResult = new AutomationTestResult();
              automationTestResult.setName(caseResult.getClassName());
              automationTestResult.setAutomationContent(caseResult.getClassName());
              automationTestResult.setExecutedEndDate(completedTime);
              automationTestResult.setExecutedStartDate(startTime);
              automationTestResult.setStatus(caseResult.isPassed() ? Status.PASSED.toString() : Status.FAILED.toString());
              automationTestResult.setTestLogs(new ArrayList<AutomationTestLog>());
              automationTestResult.setAttachments(new ArrayList<AutomationAttachment>());

              automationTestResultMap.put(caseResult.getClassName(), automationTestResult);
            }
            automationTestLog = new AutomationTestLog();
            automationTestLog.setDescription(caseResult.getName());
            automationTestLog.setExpectedResult(caseResult.getName());
            automationTestLog.setStatus(caseResult.getStatus().toString());

            automationTestResult.addTestLog(automationTestLog);
            if (caseResult.isFailed()) {
              AutomationAttachment attachment = new AutomationAttachment();
              attachment.setName(caseResult.getName().concat(EXT_TEXT_FILE));
              attachment.setData(caseResult.getErrorStackTrace());
              automationTestResult.getAttachments().add(attachment);
            }
          }
        }
      }
    }

    Iterator<String> keys = automationTestResultMap.keySet().iterator();
    String key = null;
    int totalAttachments = 0;
    File zipFile = null;
    int zipFileLength = 0;
    byte[] zipFileBytes = null;
    ZipOutputStream zipOutputStream = null;

    while (keys.hasNext()) {
      key = keys.next();
      automationTestResult = automationTestResultMap.get(key);
      totalAttachments = automationTestResult.getAttachments().size();
      if (totalAttachments > LIMIT_TXT_FILES) {
        zipFile = File.createTempFile(automationTestResult.getName(), EXT_ZIP_FILE);
        zipOutputStream = new ZipOutputStream(new FileOutputStream(zipFile));

        Map<String, Integer> attachmentNames = new HashMap<>();
        for (int i = 0; i < totalAttachments; i++) {
          AutomationAttachment attachment = automationTestResult.getAttachments().get(i);
          String attachmentName = attachment.getName();
          if (attachmentNames.containsKey(attachment.getName())) {
            Integer count = attachmentNames.get(attachment.getName());
            attachmentName = attachmentName.replace(EXT_TEXT_FILE, "_" + count + EXT_TEXT_FILE);
            attachmentNames.put(attachment.getName(), ++count);
          } else {
            attachmentNames.put(attachment.getName(), 1);
          }
          zipOutputStream.putNextEntry(new ZipEntry(attachmentName));
          zipOutputStream.write(attachment.getData().getBytes());

          zipOutputStream.closeEntry();
        }

        zipOutputStream.close();
        //get zipFile stream
        BufferedInputStream bufferedInputStream = new BufferedInputStream(new FileInputStream(zipFile));
        zipFileLength = (int) zipFile.length();
        zipFileBytes = new byte[zipFileLength];
        bufferedInputStream.read(zipFileBytes, 0, zipFileLength);
        bufferedInputStream.close();
        AutomationAttachment attachment = new AutomationAttachment();
        attachment.setData(Base64.encodeBase64String(zipFileBytes));
        attachment.setContentType("application/zip");
        attachment.setName(automationTestResult.getName() + EXT_ZIP_FILE);
        // add zip file
        automationTestResult.setAttachments(Arrays.asList(attachment));
        // remove zip tmp file
        zipFile.delete();
      } else {
        for (int i = 0; i < totalAttachments; i++) {
          AutomationAttachment attachment = automationTestResult.getAttachments().get(i);
          attachment.setContentType("text/plain");
          attachment.setData(Base64.encodeBase64String(attachment.getData().getBytes()));
        }
      }
    }
    return new ArrayList<>(automationTestResultMap.values());
  }
}
