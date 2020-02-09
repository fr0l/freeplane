/*
 *  Freeplane - mind map editor
 *  Copyright (C) 2008 Joerg Mueller, Daniel Polansky, Christian Foltin, Dimitry Polivaev
 *
 *  This file author is Christian Foltin
 *  It is modified by Dimitry Polivaev in 2008.
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 2 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.freeplane.features.icon.hierarchicalicons;

import java.util.Map;

import org.freeplane.core.extension.IExtension;
import org.freeplane.core.io.IReadCompletionListener;
import org.freeplane.features.icon.IStateIconProvider;
import org.freeplane.features.icon.IconController;
import org.freeplane.features.icon.UIIcon;
import org.freeplane.features.icon.UIIconSet;
import org.freeplane.features.map.IMapChangeListener;
import org.freeplane.features.map.INodeChangeListener;
import org.freeplane.features.map.MapChangeEvent;
import org.freeplane.features.map.MapModel;
import org.freeplane.features.map.NodeChangeEvent;
import org.freeplane.features.map.NodeDeletionEvent;
import org.freeplane.features.map.NodeModel;
import org.freeplane.features.map.NodeMoveEvent;
import org.freeplane.features.mode.Controller;
import org.freeplane.features.mode.ModeController;
import org.freeplane.features.mode.NodeHookDescriptor;
import org.freeplane.features.mode.PersistentNodeHook;
import org.freeplane.features.styles.LogicalStyleController;
import org.freeplane.features.styles.MapStyle;
import org.freeplane.features.styles.MapStyleModel;
import org.freeplane.n3.nanoxml.XMLElement;

/**
 * @author Foltin
 */
public abstract class HierarchicalIcons extends PersistentNodeHook implements INodeChangeListener, IMapChangeListener,
        IReadCompletionListener, IExtension {
	public static final String ICONS = "hierarchical_icons";

    public static void install(final ModeController modeController) {
        IconController.getController(modeController).addStateIconProvider(new IStateIconProvider() {
            @Override
            public UIIcon getStateIcon(NodeModel node) {
                AccumulatedIcons iconSet = node.getExtension(AccumulatedIcons.class);
                if(iconSet != null)
                    return new UIIconSet(iconSet.getAccumulatedIcons(), 0.75f);
                else
                    return null;
            }

            @Override
            public boolean mustIncludeInIconRegistry() {
                return false;
            }
        });
        new IconIntersectionHierarchy().installHook(modeController);
        new IconUnionHierarchy().installHook(modeController);
	}
    
	protected HierarchicalIcons(Mode mode) {
		super();
		this.mode = mode;
	}

    protected void installHook(final ModeController modeController) {
        modeController.getMapController().getReadManager().addReadCompletionListener(this);
		modeController.getMapController().addUINodeChangeListener(this);
		modeController.getMapController().addUIMapChangeListener(this);
    }



	@Override
	protected void add(final NodeModel node, final IExtension extension) {
		if(MapStyleModel.getExtension(node.getMap()) != null){
			gatherLeavesAndSetStyle(node);
			gatherLeavesAndSetParentsStyle(node);
		}
		super.add(node, extension);
	}


	@Override
	public void undoableToggleHook(NodeModel node, IExtension extension) {
		removeAnotherMode(node);
		super.undoableToggleHook(node, extension);
	}

	abstract protected void removeAnotherMode(NodeModel node);
	
	@Override
	protected IExtension createExtension(final NodeModel node, final XMLElement element) {
		return this;
	}

	/**
	 */
	private void gatherLeavesAndSetParentsStyle(final NodeModel node) {
		if (node.getChildCount() == 0) {
			for (NodeModel parent = node.getParentNode(); parent != null; parent = parent.getParentNode()) {
				AccumulatedIcons.setStyleCheckForChange(parent, mode);
			}
			return;
		}
		for (final NodeModel child : node.getChildren()) {
			gatherLeavesAndSetParentsStyle(child);
		}
	}

	/**
	 */
	private void gatherLeavesAndSetStyle(final NodeModel node) {
		node.removeExtension(AccumulatedIcons.class);
		if (node.getChildCount() == 0) {
			AccumulatedIcons.setStyleCheckForChange(node, mode);
			return;
		}
		for (final NodeModel child : node.getChildren()) {
			gatherLeavesAndSetStyle(child);
		}
	}

	@Override
	public void mapChanged(final MapChangeEvent event) {
		final MapModel map = event.getMap();
		if(map == null){
			return;
		}
		final NodeModel rootNode = map.getRootNode();
		if (!isActive(rootNode)) {
			return;
		}
		final Object property = event.getProperty();
		if(! property.equals(MapStyle.MAP_STYLES)){
			return;
		}
		gatherLeavesAndSetStyle(rootNode);
		gatherLeavesAndSetParentsStyle(rootNode);
	}

	@Override
	public void nodeChanged(final NodeChangeEvent event) {
		final NodeModel node = event.getNode();
		if (!isActive(node)) {
			return;
		}
		setStyleRecursive(node);
	}

	@Override
	public void onNodeDeleted(NodeDeletionEvent nodeDeletionEvent) {
		if (!isActive(nodeDeletionEvent.parent)) {
			return;
		}
		setStyleRecursive(nodeDeletionEvent.parent);
	}

	@Override
	public void onNodeInserted(final NodeModel parent, final NodeModel child, final int newIndex) {
		if (!isActive(parent)) {
			return;
		}
		setStyleRecursive(child);
	}

	@Override
	public void onNodeMoved(NodeMoveEvent nodeMoveEvent) {
		if (!isActive(nodeMoveEvent.newParent)) {
			return;
		}
		setStyleRecursive(nodeMoveEvent.oldParent);
		setStyleRecursive(nodeMoveEvent.child);
	}

	@Override
	public void onPreNodeDelete(NodeDeletionEvent nodeDeletionEvent) {
	}

	@Override
	public void readingCompleted(final NodeModel topNode, final Map<String, String> newIds) {
		if (!topNode.containsExtension(getClass()) && !topNode.getMap().getRootNode().containsExtension(getClass())) {
			return;
		}
		final MapModel map = topNode.getMap();
		final boolean mapStylesAreAlreadyLoaded = null != MapStyleModel.getExtension(map);
		if (mapStylesAreAlreadyLoaded) {
			gatherLeavesAndSetStyle(topNode);
			gatherLeavesAndSetParentsStyle(topNode);
		} else
			LogicalStyleController.getController().refreshMap(map);
	}

	@Override
	protected void remove(final NodeModel node, final IExtension extension) {
		removeIcons(node);
		super.remove(node, extension);
	}

	/**
	 */
	private void removeIcons(final NodeModel node) {
		AccumulatedIcons icons = node.removeExtension(AccumulatedIcons.class);
		if(icons != null){
			Controller.getCurrentModeController().getMapController().delayedNodeRefresh(node, HierarchicalIcons.ICONS, null, null);
			for (final NodeModel child : node.getChildren()) {
				removeIcons(child);
			}
		}
	}

	public static enum Mode{INTERSECTION, UNION}
	final private Mode mode;

	/**
	 */
	private void setStyleRecursive(final NodeModel node) {
		if (AccumulatedIcons.setStyleCheckForChange(node, mode) && node.getParentNode() != null) {
			setStyleRecursive(node.getParentNode());
		}
	}

	@Override
	public void onPreNodeMoved(NodeMoveEvent nodeMoveEvent) {
	}


}

@NodeHookDescriptor(hookName = "accessories/plugins/HierarchicalIcons.properties")
class IconUnionHierarchy extends HierarchicalIcons{
    public IconUnionHierarchy() {
        super(Mode.UNION);
    }
    @Override
    protected void removeAnotherMode(NodeModel node) {
        final HierarchicalIcons extension = node.getExtension(IconIntersectionHierarchy.class);
        if(extension != null)
            extension.undoableDeactivateHook(node);
    }
}

@NodeHookDescriptor(hookName = "accessories/plugins/HierarchicalIcons2.properties")
class IconIntersectionHierarchy extends HierarchicalIcons{
	public IconIntersectionHierarchy() {
	    super(Mode.INTERSECTION);
    }
	@Override
	protected void removeAnotherMode(NodeModel node) {
		final HierarchicalIcons extension = node.getExtension(IconUnionHierarchy.class);
		if(extension != null)
			extension.undoableDeactivateHook(node);
	}
}
