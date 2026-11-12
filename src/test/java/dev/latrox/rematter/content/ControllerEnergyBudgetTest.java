package dev.latrox.rematter.content;

import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ControllerEnergyBudgetTest {
    @Test void nativeAndForeignSidesShareAbsoluteControllerLimit(){
        var budget=new ControllerEnergyBudget();
        try(var tx=Transaction.openRoot()){
            assertEquals(2048,budget.insert(1,100000,2048,tx,n->n));
            assertEquals(0,budget.insert(1,100000,2048,tx,n->n));
            assertEquals(129024,budget.insert(1,200000,0,tx,n->n));
            assertEquals(0,budget.insert(1,200000,131072,tx,n->n));tx.commit();
        }
        try(var tx=Transaction.openRoot()){assertEquals(131072,budget.insert(2,200000,131072,tx,n->n));tx.commit();}
    }
    @Test void simulationAndNestedAbortDoNotConsumeAllowances(){
        var budget=new ControllerEnergyBudget();
        try(var simulation=Transaction.openRoot()){assertEquals(2048,budget.insert(1,10000,2048,simulation,n->n));}
        try(var outer=Transaction.openRoot()){
            assertEquals(1000,budget.insert(1,1000,2048,outer,n->n));
            try(var nested=Transaction.open(outer)){assertEquals(1048,budget.insert(1,10000,2048,nested,n->n));}
            assertEquals(1048,budget.insert(1,10000,2048,outer,n->n));outer.commit();
        }
        try(var tx=Transaction.openRoot()){assertEquals(0,budget.insert(1,1,2048,tx,n->n));}
    }
    @Test void rejectionLeavesEnergyAndBudgetAtSource(){
        var budget=new ControllerEnergyBudget();
        try(var tx=Transaction.openRoot()){
            assertEquals(0,budget.insert(1,2048,2048,tx,n->0));
            assertEquals(128,budget.insert(1,2048,2048,tx,n->128));
            assertEquals(1920,budget.insert(1,2048,2048,tx,n->n));tx.commit();
        }
    }
    @Test void betterNativeConnectionReplacesRateRatherThanAdding(){
        var budget=new ControllerEnergyBudget();
        try(var tx=Transaction.openRoot()){
            assertEquals(2048,budget.insert(1,99999,2048,tx,n->n));
            assertEquals(14336,budget.insert(1,99999,16384,tx,n->n));
            assertEquals(0,budget.insert(1,99999,16384,tx,n->n));tx.commit();
        }
    }
}
