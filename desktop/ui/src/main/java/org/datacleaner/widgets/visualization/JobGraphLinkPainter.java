/**
 * DataCleaner (community edition)
 * Copyright (C) 2014 Neopost - Customer Information Management
 *
 * This copyrighted material is made available to anyone wishing to use, modify,
 * copy, or redistribute it subject to the terms and conditions of the GNU
 * Lesser General Public License, as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public License
 * for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this distribution; if not, write to:
 * Free Software Foundation, Inc.
 * 51 Franklin Street, Fifth Floor
 * Boston, MA  02110-1301  USA
 */
package org.datacleaner.widgets.visualization;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Shape;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;

import org.apache.metamodel.schema.Table;
import org.datacleaner.api.InputColumn;
import org.datacleaner.descriptors.ConfiguredPropertyDescriptor;
import org.datacleaner.job.ComponentRequirement;
import org.datacleaner.job.FilterOutcome;
import org.datacleaner.job.HasFilterOutcomes;
import org.datacleaner.job.InputColumnSourceJob;
import org.datacleaner.job.SimpleComponentRequirement;
import org.datacleaner.job.builder.AnalysisJobBuilder;
import org.datacleaner.job.builder.ComponentBuilder;
import org.datacleaner.util.GraphUtils;
import org.datacleaner.util.IconUtils;
import org.datacleaner.util.ReflectionUtils;
import org.datacleaner.util.WidgetFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.uci.ics.jung.algorithms.layout.AbstractLayout;
import edu.uci.ics.jung.visualization.VisualizationServer;

/**
 * Supporting class containing the state surrounding the drawing of new
 * {@link JobGraphLink}s.
 */
public class JobGraphLinkPainter {

    private static final Logger logger = LoggerFactory.getLogger(JobGraphLinkPainter.class);

    private final JobGraphContext _graphContext;
    private final JobGraphActions _actions;
    private final VisualizationServer.Paintable _edgePaintable;
    private final VisualizationServer.Paintable _arrowPaintable;

    private Shape _edgeShape;
    private Shape _arrowShape;
    private Object _startVertex;
    private Point2D _startPoint;

    public JobGraphLinkPainter(JobGraphContext graphContext, JobGraphActions actions) {
        _graphContext = graphContext;
        _actions = actions;
        _edgePaintable = new EdgePaintable();
        _arrowPaintable = new ArrowPaintable();
    }

    /**
     * Called when the drawing of a new link/edge is started
     * 
     * @param startVertex
     */
    public void startLink(Object startVertex) {
        if (startVertex == null) {
            return;
        }

        final AbstractLayout<Object, JobGraphLink> graphLayout = _graphContext.getGraphLayout();
        int x = (int) graphLayout.getX(startVertex);
        int y = (int) graphLayout.getY(startVertex);

        logger.debug("startLink({})", startVertex);

        _startVertex = startVertex;
        _startPoint = new Point(x, y);

        transformEdgeShape(_startPoint, _startPoint);
        _graphContext.getVisualizationViewer().addPostRenderPaintable(_edgePaintable);
        transformArrowShape(_startPoint, _startPoint);
        _graphContext.getVisualizationViewer().addPostRenderPaintable(_arrowPaintable);
    }

    public boolean endLink(MouseEvent me) {
        if (_startVertex != null) {
            final Object vertex = _graphContext.getVertex(me);
            return endLink(vertex, me);
        }
        return false;
    }

    /**
     * If startVertex is non-null this method will attempt to end the
     * link-painting at the given endVertex
     * 
     * @return true if a link drawing was ended or false if it wasn't started
     */
    public boolean endLink(Object endVertex, MouseEvent mouseEvent) {
        logger.debug("endLink({})", endVertex);
        boolean result = false;
        if (_startVertex != null && endVertex != null) {
            if (mouseEvent.getButton() == MouseEvent.BUTTON1) {
                final boolean created = createLink(_startVertex, endVertex, mouseEvent);
                if (created && _graphContext.getVisualizationViewer().isVisible()) {
                    _graphContext.getJobGraph().refresh();
                }
                result = true;
            }
        }
        stopDrawing();
        return result;
    }

    private void stopDrawing() {
        _startVertex = null;
        _startPoint = null;
        _graphContext.getVisualizationViewer().removePostRenderPaintable(_edgePaintable);
        _graphContext.getVisualizationViewer().removePostRenderPaintable(_arrowPaintable);
    }

    /**
     * Cancels the drawing of the link
     */
    public void cancelLink() {
        logger.debug("cancelLink()");
        stopDrawing();
    }

    public void moveCursor(MouseEvent me) {
        if (_startVertex != null) {
            moveCursor(me.getPoint());
        }
    }

    public void moveCursor(Point2D currentPoint) {
        if (_startVertex != null) {
            logger.debug("moveCursor({})", currentPoint);
            transformEdgeShape(_startPoint, currentPoint);
            transformArrowShape(_startPoint, currentPoint);
            _graphContext.getVisualizationViewer().repaint();
        }
    }

    private boolean createLink(final Object fromVertex, final Object toVertex, final MouseEvent mouseEvent) {
        logger.debug("createLink({}, {}, {})", fromVertex, toVertex, mouseEvent);

        final List<? extends InputColumn<?>> sourceColumns;
        final Collection<FilterOutcome> filterOutcomes;

        if (fromVertex instanceof Table) {
            final Table table = (Table) fromVertex;
            final AnalysisJobBuilder analysisJobBuilder = _graphContext.getJobGraph().getAnalysisJobBuilder();
            sourceColumns = analysisJobBuilder.getSourceColumnsOfTable(table);
            filterOutcomes = null;
        } else if (fromVertex instanceof InputColumnSourceJob) {
            InputColumn<?>[] outputColumns = null;
            try {
                outputColumns = ((InputColumnSourceJob) fromVertex).getOutput();
            } catch (Exception e) {
                outputColumns = new InputColumn[0];
            }
            sourceColumns = Arrays.<InputColumn<?>> asList(outputColumns);
            filterOutcomes = null;
        } else if (fromVertex instanceof HasFilterOutcomes) {
            final HasFilterOutcomes hasFilterOutcomes = (HasFilterOutcomes) fromVertex;
            filterOutcomes = hasFilterOutcomes.getFilterOutcomes();
            sourceColumns = null;
        } else {
            sourceColumns = null;
            filterOutcomes = null;
        }

        if (toVertex instanceof ComponentBuilder) {
            final ComponentBuilder componentBuilder = (ComponentBuilder) toVertex;
            if (sourceColumns != null && !sourceColumns.isEmpty()) {
                try {
                    final ConfiguredPropertyDescriptor inputProperty = componentBuilder
                            .getDefaultConfiguredPropertyForInput();
                    if (inputProperty.isArray()) {
                        _actions.showConfigurationDialog(componentBuilder);
                        componentBuilder.addInputColumns(getRelevantSourceColumn(sourceColumns, inputProperty),
                                inputProperty);
                    } else {
                        _actions.showConfigurationDialog(componentBuilder);
                        componentBuilder.addInputColumn(sourceColumns.get(0), inputProperty);
                    }

                    // returning true to indicate a change
                    logger.debug("createLink(...) returning true - input column(s) added");
                    return true;
                } catch (Exception e) {
                    // nothing to do
                    logger.info("Failed to add input columns ({}) to {}", sourceColumns.size(), componentBuilder, e);
                }
            } else if (filterOutcomes != null && !filterOutcomes.isEmpty()) {
                final JPopupMenu popup = new JPopupMenu();
                for (final FilterOutcome filterOutcome : filterOutcomes) {
                    final JMenuItem menuItem = WidgetFactory.createMenuItem(filterOutcome.getSimpleName(),
                            IconUtils.FILTER_OUTCOME_PATH);
                    menuItem.addActionListener(new ActionListener() {
                        @Override
                        public void actionPerformed(ActionEvent e) {
                            final ComponentRequirement requirement = new SimpleComponentRequirement(filterOutcome);
                            componentBuilder.setComponentRequirement(requirement);
                        }
                    });
                    popup.add(menuItem);
                }
                popup.show(_graphContext.getVisualizationViewer(), mouseEvent.getX(), mouseEvent.getY());

                // we return false because no change was applied (yet)
                logger.debug("createLink(...) returning false - popup with choices presented to user");
                return false;
            }
        }
        logger.debug("createLink(...) returning false - no applicable action");
        return false;
    }

    private Collection<? extends InputColumn<?>> getRelevantSourceColumn(List<? extends InputColumn<?>> sourceColumns,
            ConfiguredPropertyDescriptor inputProperty) {
        assert inputProperty.isInputColumn();

        List<InputColumn<?>> result = new ArrayList<>();
        final Class<?> expectedDataType = inputProperty.getTypeArgument(0);
        for (InputColumn<?> inputColumn : sourceColumns) {
            final Class<?> actualDataType = inputColumn.getDataType();
            if (ReflectionUtils.is(actualDataType, expectedDataType, false)) {
                result.add(inputColumn);
            }
        }

        return result;
    }

    private void transformEdgeShape(Point2D down, Point2D out) {
        Shape shape = new Line2D.Float(down, out);
        _edgeShape = shape;
        return;
    }

    private void transformArrowShape(Point2D down, Point2D out) {
        float x1 = (float) down.getX();
        float y1 = (float) down.getY();
        float x2 = (float) out.getX();
        float y2 = (float) out.getY();

        AffineTransform xform = AffineTransform.getTranslateInstance(x2, y2);

        float dx = x2 - x1;
        float dy = y2 - y1;
        float thetaRadians = (float) Math.atan2(dy, dx);
        xform.rotate(thetaRadians);
        _arrowShape = xform.createTransformedShape(GraphUtils.ARROW_SHAPE);
    }

    /**
     * Used for the edge creation visual effect during mouse drag
     */
    class EdgePaintable implements VisualizationServer.Paintable {

        public void paint(Graphics g) {
            if (_edgeShape != null) {
                Color oldColor = g.getColor();
                g.setColor(Color.black);
                ((Graphics2D) g).draw(_edgeShape);
                g.setColor(oldColor);
            }
        }

        public boolean useTransform() {
            return false;
        }
    }

    /**
     * Used for the directed edge creation visual effect during mouse drag
     */
    class ArrowPaintable implements VisualizationServer.Paintable {

        public void paint(Graphics g) {
            if (_arrowShape != null) {
                Color oldColor = g.getColor();
                g.setColor(Color.black);
                ((Graphics2D) g).fill(_arrowShape);
                g.setColor(oldColor);
            }
        }

        public boolean useTransform() {
            return false;
        }
    }
}
