/*
 *
 * The DbUnit Database Testing Framework
 * Copyright (C)2002-2024, DbUnit.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 *
 */

package org.dbunit.ant.adapter;

import java.io.File;
import java.io.OutputStream;
import java.io.PrintStream;
import org.apache.tools.ant.BuildEvent;
import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.BuildListener;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.ProjectHelper;
import org.apache.tools.ant.util.ProcessUtil;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * The BuildFileExtension class provides functionality to configure and execute Apache Ant build
 * files for testing purposes. It has been ported over from the ant-testutils.jar which only
 * supports junit4. See BuildFileRule for comparison.
 *
 * @author Andrew Johnson
 * @author Last changed by: $Author$
 * @version $Revision$ $Date$
 * @see org.apache.tools.ant.BuildFileRule
 * @since Sep 21, 2024
 */
public class BuildFileExtension implements AfterEachCallback {

	private Project project;
	private StringBuilder logBuffer;
	private StringBuilder fullLogBuffer;
	private StringBuilder outputBuffer;
	private StringBuilder errorBuffer;

	public BuildFileExtension() {
	}

	protected void after() {
		if (this.project != null) {
			String tearDown = "tearDown";
			if (this.project.getTargets().containsKey("tearDown")) {
				this.project.executeTarget("tearDown");
			}

		}
	}

	public String getLog() {
		return this.logBuffer.toString();
	}

	public String getFullLog() {
		return this.fullLogBuffer.toString();
	}

	public String getOutput() {
		return this.cleanBuffer(this.outputBuffer);
	}

	public String getError() {
		return this.cleanBuffer(this.errorBuffer);
	}

	private String cleanBuffer(StringBuilder buffer) {
		StringBuilder cleanedBuffer = new StringBuilder();

		for (int i = 0; i < buffer.length(); ++i) {
			char ch = buffer.charAt(i);
			if (ch != '\r') {
				cleanedBuffer.append(ch);
			}
		}

		return cleanedBuffer.toString();
	}

	public void configureProject(String filename) throws BuildException {
		this.configureProject(filename, 4);
	}

	public void configureProject(String filename, int logLevel) throws BuildException {
		this.logBuffer = new StringBuilder();
		this.fullLogBuffer = new StringBuilder();
		this.project = new Project();
		if (Boolean.getBoolean("ant.test.basedir.ignore")) {
			System.clearProperty("basedir");
		}

		this.project.init();
		File antFile = new File(System.getProperty("root"), filename);
		this.project.setProperty("ant.processid", ProcessUtil.getProcessId("<Process>"));
		this.project.setProperty("ant.threadname", Thread.currentThread().getName());
		this.project.setUserProperty("ant.file", antFile.getAbsolutePath());
		this.project.addBuildListener(new AntTestListener(logLevel));
		ProjectHelper.configureProject(this.project, antFile);
	}

	public void executeTarget(String targetName) {
		this.outputBuffer = new StringBuilder();
		PrintStream out = new PrintStream(
				new AntOutputStream(this.outputBuffer));
		this.errorBuffer = new StringBuilder();
		PrintStream err = new PrintStream(
				new AntOutputStream(this.errorBuffer));
		this.logBuffer = new StringBuilder();
		this.fullLogBuffer = new StringBuilder();
		synchronized (System.out) {
			PrintStream sysOut = System.out;
			PrintStream sysErr = System.err;
			sysOut.flush();
			sysErr.flush();

			try {
				System.setOut(out);
				System.setErr(err);
				this.project.executeTarget(targetName);
			} finally {
				System.setOut(sysOut);
				System.setErr(sysErr);
			}

		}
	}

	public Project getProject() {
		return this.project;
	}

	public File getOutputDir() {
		return new File(this.getProject().getProperty("output"));
	}

	@Override
	public void afterEach(ExtensionContext context) throws Exception {
		after();
	}

	protected static class AntOutputStream extends OutputStream {

		private StringBuilder buffer;

		public AntOutputStream(StringBuilder buffer) {
			this.buffer = buffer;
		}

		public void write(int b) {
			this.buffer.append((char) b);
		}
	}

	private class AntTestListener implements BuildListener {

		private int logLevel;

		public AntTestListener(int logLevel) {
			this.logLevel = logLevel;
		}

		public void buildStarted(BuildEvent event) {
		}

		public void buildFinished(BuildEvent event) {
		}

		public void targetStarted(BuildEvent event) {
		}

		public void targetFinished(BuildEvent event) {
		}

		public void taskStarted(BuildEvent event) {
		}

		public void taskFinished(BuildEvent event) {
		}

		public void messageLogged(BuildEvent event) {
			if (event.getPriority() <= this.logLevel) {
				if (event.getPriority() == 2 || event.getPriority() == 1 || event.getPriority() == 0) {
					logBuffer.append(event.getMessage());
				}

				fullLogBuffer.append(event.getMessage());
			}
		}
	}
}
