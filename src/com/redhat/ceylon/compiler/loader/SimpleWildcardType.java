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
package com.redhat.ceylon.compiler.loader;

import java.util.Collections;
import java.util.List;

import javax.lang.model.type.TypeKind;

import com.redhat.ceylon.compiler.loader.mirror.ClassMirror;
import com.redhat.ceylon.compiler.loader.mirror.TypeMirror;
import com.redhat.ceylon.compiler.loader.mirror.TypeParameterMirror;

/**
 * Simple Wildcard Mirror.
 *
 * @author Stéphane Épardaud <stef@epardaud.fr>
 */
public class SimpleWildcardType implements TypeMirror {
 
    private TypeMirror bound;

    public SimpleWildcardType(TypeMirror bound) {
        this.bound = bound;
    }
    
    public String toString() {
        return "? extends "+bound;
    }

    @Override
    public String getQualifiedName() {
        return bound.getQualifiedName();
    }

    @Override
    public List<TypeMirror> getTypeArguments() {
        return Collections.emptyList();
    }

    @Override
    public TypeKind getKind() {
        return TypeKind.WILDCARD;
    }

    @Override
    public TypeMirror getComponentType() {
        return null;
    }

    @Override
    public boolean isPrimitive() {
        return false;
    }

    @Override
    public TypeMirror getUpperBound() {
        return bound;
    }

    @Override
    public TypeMirror getLowerBound() {
        return null;
    }

    @Override
    public boolean isRaw() {
        return false;
    }

    @Override
    public ClassMirror getDeclaredClass() {
        return null;
    }

    @Override
    public TypeParameterMirror getTypeParameter() {
        return null;
    }
}
