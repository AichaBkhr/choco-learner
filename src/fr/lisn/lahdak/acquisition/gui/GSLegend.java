package fr.lisn.lahdak.acquisition.gui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.stream.Collectors;

import javax.swing.JLabel;
import javax.swing.JPanel;

import org.graphstream.graph.Graph;
import org.graphstream.graph.Node;
import org.graphstream.graph.implementations.MultiGraph;
import org.graphstream.ui.spriteManager.Sprite;
import org.graphstream.ui.spriteManager.SpriteManager;
import org.graphstream.ui.view.Viewer;

public class GSLegend extends JPanel{

    static {
        System.setProperty("org.graphstream.ui.renderer", "org.graphstream.ui.j2dviewer.J2DGraphRenderer");
    }
    protected Viewer viewer;    
    protected Graph graph;
    protected int nb_variables = 0;
    protected int nb_constraints = 0;
    protected JLabel label_constraints = new JLabel("?");
    protected JLabel label_variables = new JLabel("?");

    public GSLegend(String cssPath,String[] constraintNames) {
        super(new BorderLayout());
        this.setMinimumSize(new Dimension(100,100));
        graph = new MultiGraph("legend");
        graph.setAttribute("ui.quality");
        graph.setAttribute("ui.antialias");
        attachStyle(graph, cssPath);
        viewer = graph.display(false);
        //GraphRenderer renderer = viewer.newDefaultGraphRenderer();
        
        this.add(new JLabel("Legend        "),BorderLayout.NORTH);
        //this.add(viewer.addView(Viewer.DEFAULT_VIEW_ID, renderer, false), BorderLayout.CENTER);
        SpriteManager sman = new SpriteManager(graph);
        Node n = graph.addNode("variable");
        n.setAttribute("ui.class", "variable");
        n.setAttribute("xyz", 10,10,0);
//        n.addAttribute("ui.label", "Variable");
        Sprite s = sman.addSprite("Variable");
        s.attachToNode(n.getId());
        s.setPosition(20, 0, 0);
        for(int i=0;i<constraintNames.length;i++)
        {
        String constraintName=constraintNames[i];
        n = graph.addNode(constraintName);
        n.setAttribute("ui.class", constraintName);
        n.setAttribute("xyz", 10,10*(i+2),0);
        s = sman.addSprite(constraintName);
        s.attachToNode(n.getId());
        s.setPosition(20, 0, 0);
//        n.addAttribute("ui.label", constraintName);
        }
    }
    public void close() {
        viewer.close();
    }

    public static void attachStyle(Graph g, String cssPath) {
    String cssString = null;
    if (cssPath != null) {
        try (BufferedReader reader = new BufferedReader(new FileReader(cssPath))) {
            cssString = reader.lines().collect(Collectors.joining("\n"));
        } catch (FileNotFoundException ex) {
            ex.printStackTrace();
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }
    if (cssString == null) {
        InputStream inputStream = GSLegend.class.getResourceAsStream("/fr/lisn/lahdak/acquisition/resource/style.css");
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
            cssString = reader.lines().collect(Collectors.joining("\n"));
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }
    g.setAttribute("ui.stylesheet", cssString);
}
}
