package com.redhat.ceylon.compiler.java.test.expression.invoke;

abstract class TypeFamilyGeneric<N extends com.redhat.ceylon.compiler.java.test.expression.invoke.TypeFamilyGeneric<N>.Node> {
    
    public class Node {
        
        public Node() {
        }
    }
    
    TypeFamilyGeneric() {
    }
}
interface Inv<T> {
}
final class Inv$impl<T> {
    private final com.redhat.ceylon.compiler.java.test.expression.invoke.Inv<T> $this;
    
    Inv$impl(com.redhat.ceylon.compiler.java.test.expression.invoke.Inv<T> $this) {
        this.$this = $this;
    }
}
final class typeFamilyGeneric {
    
    private typeFamilyGeneric() {
    }
    
    static <X extends com.redhat.ceylon.compiler.java.test.expression.invoke.TypeFamilyGeneric<X>.Node>void typeFamilyGeneric() {
        
        final class l1$getter {
            
            com.redhat.ceylon.compiler.java.test.expression.invoke.Inv<com.redhat.ceylon.compiler.java.test.expression.invoke.TypeFamilyGeneric<X>.Node> getL1() {
                throw new ceylon.language.Exception(null, null);
            }
            
            private l1$getter() {
            }
        }
        final l1$getter l1$getter = new l1$getter();
        
        final class l2$getter {
            
            com.redhat.ceylon.compiler.java.test.expression.invoke.Inv<X> getL2() {
                throw new ceylon.language.Exception(null, null);
            }
            
            private l2$getter() {
            }
        }
        final l2$getter l2$getter = new l2$getter();
        final com.redhat.ceylon.compiler.java.test.expression.invoke.Inv<com.redhat.ceylon.compiler.java.test.expression.invoke.TypeFamilyGeneric<X>.Node> l3 = (com.redhat.ceylon.compiler.java.test.expression.invoke.Inv<com.redhat.ceylon.compiler.java.test.expression.invoke.TypeFamilyGeneric<X>.Node>)(com.redhat.ceylon.compiler.java.test.expression.invoke.Inv)l2$getter.getL2();
        final com.redhat.ceylon.compiler.java.test.expression.invoke.Inv<X> l4 = (com.redhat.ceylon.compiler.java.test.expression.invoke.Inv<X>)(com.redhat.ceylon.compiler.java.test.expression.invoke.Inv)l1$getter.getL1();
    }
    
    public static void main(java.lang.String[] args) {
        ceylon.language.process.getProcess().setupArguments(args);
        com.redhat.ceylon.compiler.java.test.expression.invoke.typeFamilyGeneric.typeFamilyGeneric();
    }
}
