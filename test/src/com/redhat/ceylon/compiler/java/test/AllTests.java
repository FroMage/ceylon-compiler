/*
 * Copyright Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the authors tag. All rights reserved.
 *
 * This copyrighted material is made available to anyone wishing to use,
 * modify, copy, or redistribute it subject to the terms and conditions
 * of the GNU General Public License version 2.
 * 
 * This particular file is subject to the "Classpath" exception as provided in the 
 * LICENSE file that accompanied this code.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT A
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE.  See the GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License,
 * along with this distribution; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA  02110-1301, USA.
 */
package com.redhat.ceylon.compiler.java.test;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;

import com.redhat.ceylon.ant.AntToolTests;
import com.redhat.ceylon.ceylondoc.test.CeylonDocToolTest;
import com.redhat.ceylon.compiler.java.codegen.NamingTest;
import com.redhat.ceylon.compiler.java.test.annotations.AnnotationsTest;
import com.redhat.ceylon.compiler.java.test.bc.BcTests;
import com.redhat.ceylon.compiler.java.test.cmr.CMRTest;
import com.redhat.ceylon.compiler.java.test.cmr.CMRTestHTTP;
import com.redhat.ceylon.compiler.java.test.expression.ExpressionTest;
import com.redhat.ceylon.compiler.java.test.expression.ExpressionTest2;
import com.redhat.ceylon.compiler.java.test.expression.ExpressionTest3;
import com.redhat.ceylon.compiler.java.test.interop.InteropTest;
import com.redhat.ceylon.compiler.java.test.issues.IssuesTest;
import com.redhat.ceylon.compiler.java.test.issues.PackageIssuesTest;
import com.redhat.ceylon.compiler.java.test.language.LanguageSuite;
import com.redhat.ceylon.compiler.java.test.languagesatisfaction.LanguageSatisfactionSuite;
import com.redhat.ceylon.compiler.java.test.misc.MiscTest;
import com.redhat.ceylon.compiler.java.test.model.ModelLoaderTest;
import com.redhat.ceylon.compiler.java.test.model.TypeParserTest;
import com.redhat.ceylon.compiler.java.test.quoting.QuotingTest;
import com.redhat.ceylon.compiler.java.test.recovery.RecoveryTest;
import com.redhat.ceylon.compiler.java.test.runtime.RuntimeSuite;
import com.redhat.ceylon.compiler.java.test.statement.StatementTest;
import com.redhat.ceylon.compiler.java.test.structure.StructureTest;
import com.redhat.ceylon.compiler.java.test.structure.StructureTest2;
import com.redhat.ceylon.compiler.java.test.structure.StructureTest3;
import com.redhat.ceylon.tools.test.CompilerToolsTests;

@RunWith(Suite.class) 
@SuiteClasses({
    NamingTest.class,
    AnnotationsTest.class,
    ExpressionTest.class,
    ExpressionTest2.class,
    ExpressionTest3.class,
    InteropTest.class,
    IssuesTest.class,
	MiscTest.class,
    ModelLoaderTest.class,
    PackageIssuesTest.class,
    RecoveryTest.class,
    StatementTest.class,
	StructureTest.class,
    StructureTest2.class,
    StructureTest3.class,
	TypeParserTest.class,
	QuotingTest.class,
	CMRTest.class,
    CMRTestHTTP.class,
	RuntimeSuite.class,
	LanguageSatisfactionSuite.class,
	LanguageSuite.class,
	BcTests.class,
    CompilerToolsTests.class,
    com.redhat.ceylon.tools.TopLevelToolTest.class,
    CeylonDocToolTest.class,
    AntToolTests.class
})
public class AllTests {
}
