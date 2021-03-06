/*******************************************************************************
 * Copyright (c) 2019 Red Hat Inc. and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.internal.corext.fix;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.runtime.IStatus;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ArrayCreation;
import org.eclipse.jdt.core.dom.ArrayInitializer;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.SuperMethodInvocation;

import org.eclipse.jdt.internal.corext.dom.GenericVisitor;
import org.eclipse.jdt.internal.corext.util.JavaModelUtil;

import org.eclipse.jdt.ui.cleanup.ICleanUpFix;

public class UnnecessaryArrayCreationFix extends CompilationUnitRewriteOperationsFix {

	public final static class UnnecessaryArrayCreationFinder extends GenericVisitor {

		private final List<CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperation> fResult;
		private final boolean fRemoveUnnecessaryArrayCreation;

		public UnnecessaryArrayCreationFinder(boolean removeUnnecessaryArrayCreation, List<CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperation> resultingCollection) {
			fRemoveUnnecessaryArrayCreation= removeUnnecessaryArrayCreation;
			fResult= resultingCollection;
		}

		@SuppressWarnings("rawtypes")
		@Override
		public boolean visit(ArrayCreation node) {
			if (fRemoveUnnecessaryArrayCreation) {
				ASTNode parent= node.getParent();
				if (parent instanceof MethodInvocation) {
					MethodInvocation m= (MethodInvocation)parent;
					List arguments= m.arguments();
					ArrayInitializer initializer= node.getInitializer();
					if (arguments.size() > 0 && arguments.get(arguments.size() - 1) == node
							&& node.getType().getDimensions() == 1
							&& initializer != null && initializer.expressions() != null) {
						IMethodBinding binding= m.resolveMethodBinding();
						if (binding != null && binding.isVarargs() && binding.getParameterTypes().length == arguments.size()) {
							fResult.add(new UnwrapNewArrayOperation(node, m));
						}
					}
				} else if (parent instanceof SuperMethodInvocation) {
					SuperMethodInvocation m= (SuperMethodInvocation)parent;
					List arguments= m.arguments();
					ArrayInitializer initializer= node.getInitializer();
					if (arguments.size() > 0 && arguments.get(arguments.size() - 1) == node
							&& node.getType().getDimensions() == 1
							&& initializer != null && initializer.expressions() != null) {
						IMethodBinding binding= m.resolveMethodBinding();
						if (binding != null && binding.isVarargs() && binding.getParameterTypes().length == arguments.size()) {
							fResult.add(new UnwrapNewArrayOperation(node, m));
						}
					}
				}
			}
			return true;
		}

	}

	public static ICleanUpFix createCleanUp(CompilationUnit compilationUnit, boolean removeUnnecessaryArrayCreation) {
		if (!JavaModelUtil.is50OrHigher(compilationUnit.getJavaElement().getJavaProject()))
			return null;

		if (!removeUnnecessaryArrayCreation)
			return null;

		List<CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperation> operations= new ArrayList<>();
		UnnecessaryArrayCreationFix.UnnecessaryArrayCreationFinder finder= new UnnecessaryArrayCreationFix.UnnecessaryArrayCreationFinder(removeUnnecessaryArrayCreation, operations);
		compilationUnit.accept(finder);

		if (operations.isEmpty())
			return null;

		CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperation[] ops= operations.toArray(new CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperation[operations.size()]);
		return new ConvertLoopFix(FixMessages.ControlStatementsFix_change_name, compilationUnit, ops, null);
	}

	public static UnnecessaryArrayCreationFix createUnnecessaryArrayCreationFix(CompilationUnit compilationUnit, Expression methodInvocation) {
		if (!JavaModelUtil.is50OrHigher(compilationUnit.getJavaElement().getJavaProject()))
			return null;

		List<CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperation> operations= new ArrayList<>();
		UnnecessaryArrayCreationFix.UnnecessaryArrayCreationFinder finder= new UnnecessaryArrayCreationFix.UnnecessaryArrayCreationFinder(true, operations);
		methodInvocation.accept(finder);

		if (operations.isEmpty())
			return null;

		return new UnnecessaryArrayCreationFix(FixMessages.Java50Fix_RemoveUnnecessaryArrayCreation_description, compilationUnit, new CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperation[] {operations.get(0)}, null);
	}

	private final IStatus fStatus;

	protected UnnecessaryArrayCreationFix(String name, CompilationUnit compilationUnit, CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperation[] fixRewriteOperations, IStatus status) {
		super(name, compilationUnit, fixRewriteOperations);
		fStatus= status;
	}

	@Override
	public IStatus getStatus() {
		return fStatus;
	}

}
