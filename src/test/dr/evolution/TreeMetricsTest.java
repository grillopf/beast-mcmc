package test.dr.evolution;

import dr.evolution.io.Importer;
import dr.evolution.io.NewickImporter;
import dr.evolution.tree.*;
import jebl.evolution.treemetrics.BilleraMetric;
//import jebl.evolution.treemetrics.CladeHeightMetric;
import jebl.evolution.treemetrics.RobinsonsFouldMetric;
import junit.framework.TestCase;

import java.io.*;

/**
 * @author Luiz Carvalho
 */
public class TreeMetricsTest extends TestCase {

    public static void main(String[] args) {

        try {

            NewickImporter importer = new NewickImporter("((A:0.1,B:0.1):0.1,(C:0.1,D:0.1):0.1)");
            Tree treeOne = importer.importNextTree();
            System.out.println("tree 1: " + treeOne);

            importer = new NewickImporter("(((A:0.1,B:0.1):0.5,C:0.1):0.1,D:0.1)");
            Tree treeTwo = importer.importNextTree();
            System.out.println("tree 2: " + treeTwo + "\n");
            
            /* Billera et al., 2001 */
            
//            double billera = (new BilleraMetric().getMetric(TreeUtils.asJeblTree(treeOne),
//            		TreeUtils.asJeblTree(treeTwo)));
//
//            System.out.println("Billera distance = " + billera);
//            assertEquals(billera, 0.2236068);
            
            /* Robinson & Foulds, 1981*/
            double RF = (new RobinsonsFouldMetric().getMetric(TreeUtils.asJeblTree(treeOne), TreeUtils.asJeblTree(treeTwo))*2.0);
            System.out.println("Robinson-Foulds = " + RF);
            assertEquals(RF, 2.0, 0.0000001);
            
            /* Penny and Hendy, 1993*/
            double path = (new SPPathDifferenceMetric().getMetric(treeOne, treeTwo));
            System.out.println("path difference = " + path);
            assertEquals(path, 0.7141428, 0.0000001);
            
            /* Branch Score*/
            double bl = (new BranchScoreMetric().getMetric(TreeUtils.asJeblTree(treeOne),
            		TreeUtils.asJeblTree(treeTwo)));
            System.out.println("bl score = " + bl);
            assertEquals(bl, 0.3, 0.0000001);
            

        } catch(Importer.ImportException ie) {
            System.err.println(ie);
        } catch(IOException ioe) {
            System.err.println(ioe);
        }

    }

}

