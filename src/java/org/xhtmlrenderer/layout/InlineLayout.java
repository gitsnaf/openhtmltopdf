/*
* {{{ header & license
* Copyright (c) 2004 Joshua Marinacci
*
* This program is free software; you can redistribute it and/or
* modify it under the terms of the GNU Lesser General Public License
* as published by the Free Software Foundation; either version 2.1
* of the License, or (at your option) any later version.
*
* This program is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.	See the
* GNU Lesser General Public License for more details.
*
* You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 * }}}
 */
 package org.xhtmlrenderer.layout;
import java.awt.Color;
import java.awt.Font;
import java.awt.Rectangle;
import java.util.List;
import java.util.ArrayList;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xhtmlrenderer.layout.inline.*;
import org.xhtmlrenderer.render.AnonymousBlockBox;
import org.xhtmlrenderer.render.BlockBox;
import org.xhtmlrenderer.render.Box;
import org.xhtmlrenderer.render.InlineBox;
//import org.xhtmlrenderer.render.InlinePainter;
import org.xhtmlrenderer.render.LineBox;
import org.xhtmlrenderer.render.InlineRenderer;
import org.xhtmlrenderer.render.Renderer;
import org.xhtmlrenderer.css.value.*;
import org.xhtmlrenderer.css.*;
import org.xhtmlrenderer.util.InfiniteLoopError;
import org.xhtmlrenderer.util.u;
import org.xhtmlrenderer.util.XRLog;
/**
* Description of the Class
*
* @author   empty
*/
public class InlineLayout extends BoxLayout {
    /**
    * Description of the Method
    *
    * @param c    PARAM
    * @param box  PARAM
    * @return     Returns
    */
    public Box layoutChildren( Context c, Box box ) {
        //u.p("starting to lay out the children");
        if ( LayoutUtil.isHiddenNode( box.getElement(), c ) ) {
            return box;
        }
        if ( !box.isAnonymous() ) {
            if ( LayoutUtil.isBlockLayout( box.getElement(), c ) ) {
                //u.p("doing up block for: " + box);
                return super.layoutChildren( c, box );
            }
        }
        
        int debug_counter = 0;
        int childcount = 0;
        BlockBox block = (BlockBox)box;
        // calculate the initial position and dimensions
        Rectangle bounds = new Rectangle();
        bounds.width = c.getExtents().width;
        bounds.width -= box.margin.left + box.border.left + box.padding.left +
            box.padding.right + box.border.right + box.margin.right;
        validateBounds(bounds);
        bounds.x = 0;
        bounds.y = 0;
        bounds.height = 0;
        //block.width = bounds.width;
        //u.p("child layout: " + block);
        int remaining_width = bounds.width;
        
        
        LineBox curr_line = new LineBox();
        c.setFirstLine(true);
        curr_line.setParent(box);
        curr_line.x = bounds.x;
        //curr_line.width = remaining_width;
        curr_line.width = 0;
        // account for text-indent
        Element elem = block.getElement();
        remaining_width = InlineUtil.doTextIndent( c, elem, remaining_width, curr_line );
        LineBox prev_line = new LineBox();
        prev_line.setParent(box);
        prev_line.y = bounds.y;
        prev_line.height = 0;
        InlineBox prev_inline = null;
        
        InlineBox prev_align_inline = null;
        List inline_node_list = null;
        if ( box.isAnonymous() ) {
            inline_node_list = ( (AnonymousBlockBox)box ).node_list;
        } else {
            inline_node_list = InlineUtil.getInlineNodeList( elem, elem, c );
        }
        
        // loop until no more nodes
        Node current_node = InlineUtil.nextTextNode( inline_node_list );
        TextUtil.stripWhitespace( c, current_node, elem );
        // adjust the first line for tabs
        
        remaining_width = FloatUtil.adjustForTab( c, prev_line, remaining_width );
        while ( current_node != null ) {
            // loop until no more text in this node
            while ( true ) {
                
                
                
                // debugging check
                if ( bounds.width < 0 ) {
                    u.p( "bounds width = " + bounds.width );
                    u.dump_stack();
                    System.exit( -1 );
                }
                
                
                
                
                // test if there is no more text in the current text node
                // if there is a prev, and if the prev was part of this current node
                if ( prev_inline != null && prev_inline.node == current_node ) {
                    // replaced elements aren't split, so done with this one
                    if ( LayoutUtil.isReplaced(c, current_node ) ) {
                        break;
                    }
                    if ( LayoutUtil.isFloatedBlock( current_node, c ) ) {
                        break;
                    }
                    if ( c.getRenderingContext().getLayoutFactory().isBreak( current_node ) ) {
                        break;
                    }
                    // if no more unused text in this node
                    if ( prev_inline.end_index >= current_node.getNodeValue().length() ) {
                        // then break
                        break;
                    }
                }
                
                
                
                
                // the crash warning code
                if ( bounds.width < 1 ) {
                    u.p( "warning. width < 1 " + bounds.width );
                }
                
                
                // look at current inline
                // break off the longest section that will fit
                InlineBox new_inline = calculateInline( c, current_node, remaining_width, bounds.width,
                    curr_line, prev_inline, elem, prev_align_inline );
                // if this inline needs to be on a new line
                if ( new_inline.break_before && !new_inline.floated ) {
                    // u.p("breaking before");
                    // finish up the current line
                    remaining_width = bounds.width;
                    saveLine( curr_line, prev_line, elem, bounds.width, bounds.x, c, block , false);
                    bounds.height += curr_line.height;
                    prev_line = curr_line;
                    curr_line = new LineBox();
                    curr_line.x = bounds.x;
                    // adjust remaining width for floats
                    curr_line.y = prev_line.y + prev_line.height;
                    curr_line.setParent(prev_line.getParent());
                    //u.p("set parent to: " + curr_line.getParent());
                    remaining_width = FloatUtil.adjustForTab( c, curr_line, remaining_width );
                    curr_line.width = 0;
                }
                
                
                
                // save the new inline to the list
                curr_line.addChild( new_inline );
                // calc new height of the line
                // don't count the inline towards the line height and
                //line baseline if it's a floating inline.
                if ( !LayoutUtil.isFloated( new_inline, c ) ) {
                    if ( !LayoutUtil.isFloatedBlock( new_inline.node, c ) ) {
                        //u.p("calcing new height of line");
                        if ( new_inline.height + new_inline.y > curr_line.height ) {
                            curr_line.height = new_inline.height + new_inline.y;
                        }
                        if ( new_inline.baseline > curr_line.baseline ) {
                            curr_line.baseline = new_inline.baseline;
                        }
                    }
                }
                
                //u.p("curr line: " + curr_line);
                //u.p("parent = " + curr_line.getParent());
                FloatUtil.handleFloated( c, new_inline, curr_line, bounds.width, elem );
                // calc new width of the line
                curr_line.width += new_inline.width;
                // reduce the available width
                remaining_width = remaining_width - new_inline.width;
                // if the last inline was at the end of a line, then go to next line
                if ( new_inline.break_after ) {
                    // u.p("breaking after");
                    // then remaining_width = max_width
                    remaining_width = bounds.width;
                    // save the line
                    saveLine( curr_line, prev_line, elem, bounds.width, bounds.x, c, block , false);
                    // increase bounds height to account for the new line
                    bounds.height += curr_line.height;
                    prev_line = curr_line;
                    curr_line = new LineBox();
                    curr_line.x = bounds.x;
                    // adjust remaining width for floats
                    curr_line.y = prev_line.y + prev_line.height;
                    curr_line.setParent(prev_line.getParent());
                    //u.p("set parent to: " + curr_line.getParent());
                    remaining_width = FloatUtil.adjustForTab( c, curr_line, remaining_width );
                    curr_line.width = 0;
                }
                
                // set the inline to use for left alignment
                if ( !LayoutUtil.isFloated( new_inline, c ) ) {
                    prev_align_inline = new_inline;
                } else {
                    prev_align_inline = prev_inline;
                }
                prev_inline = new_inline;
            }
            current_node = InlineUtil.nextTextNode( inline_node_list );
            TextUtil.stripWhitespace( c, current_node, elem );
        }
        saveLine( curr_line, prev_line, elem, bounds.width, bounds.x, c, block , true);
        bounds.height += curr_line.height;
        block.width = bounds.width;
        block.height = bounds.height;
        block.x = 0;
        block.y = 0;
        return block;
    }
    
    private void validateBounds(Rectangle bounds) {
        if(bounds.width <= 0) {
            bounds.width = 1;
            XRLog.exception("width < 1");
        }
    }

     
    /**
    * Get the longest inline possible.
    *
    * @param c                 PARAM
    * @param node              PARAM
    * @param avail             PARAM
    * @param max_width         PARAM
    * @param line              PARAM
    * @param prev              PARAM
    * @param containing_block  PARAM
    * @param prev_align        PARAM
    * @return                  Returns
    */
    private InlineBox calculateInline( Context c, Node node, int avail, int max_width,
            LineBox line, InlineBox prev, Element containing_block, InlineBox prev_align ) {
                
        
        // calculate the starting index
        int start = 0;
        // if this is another box from the same node as the previous one
        if ( prev != null && prev.node == node ) {
            start = prev.end_index;
        }
        // get the text of the node
        String text = node.getNodeValue();
        // transform the text if required (like converting to caps)
        // this must be done before any measuring since it might change the
        // size of the text
        text = TextUtil.transformText( c, node, text );
        
        // u.p("calculating inline: text = " + text);
        // u.p("avail space = " + avail + " max = " + max_width + "   start index = " + start);

        // get the current font. required for sizing
        Font font = FontUtil.getFont( c, node );
        
        // handle each case
        if ( LayoutUtil.isReplaced(c, node ) ) {
            //u.p("is replaced");
            return LineBreaker.generateReplacedInlineBox( c, node, avail, prev, text, prev_align, font );
        }
        //u.p("calc inline on node : " + node);
        if ( LayoutUtil.isFloatedBlock( node, c ) ) {
            //u.p("calcinline: is floated block");
            return FloatUtil.generateFloatedBlockInlineBox( c, node, avail, prev, text, prev_align, font );
        }
        if ( LineBreaker.isFirstLetter( c, node, start ) ) {
            // u.p("is first letter");
            return LineBreaker.generateFirstLetterInlineBox( c, node, start, text, prev, prev_align, avail);
        }
        if ( c.getRenderingContext().getLayoutFactory().isBreak( node ) ) {
            // u.p("is break");
            return LineBreaker.generateBreakInlineBox( node );
        }
        if ( LineBreaker.isWhitespace( c, containing_block ) ) {
            // u.p("is whitespace");
            return LineBreaker.generateWhitespaceInlineBox( c, node, start, prev, text, prev_align, font );
        }
        // ==== unbreakable long word =====
        if ( LineBreaker.isUnbreakableLine( c, node, start, text, avail, font ) ) {
            // u.p("is unbreakable");
            return LineBreaker.generateUnbreakableInlineBox( c, node, start, text, prev, prev_align, font );
        }
        // rest of this string can fit on the line
        if ( LineBreaker.canFitOnLine( c, node, start, text, avail, font ) ) {
            // u.p("can fit on line");
            return LineBreaker.generateRestOfTextNodeInlineBox( c, node, start, text, prev, prev_align, font );
        }
        // normal multiline break
        // u.p("normal multi line break");
        return LineBreaker.generateMultilineBreak( c, node, start, text, prev, prev_align, avail );
    }
    
    
    
    /**
    * Description of the Method
    *
    * @param line_to_save      PARAM
    * @param prev_line         PARAM
    * @param containing_block  PARAM
    * @param width             PARAM
    * @param x                 PARAM
    * @param c                 PARAM
    * @param block             PARAM
    */
    private void saveLine( LineBox line_to_save, LineBox prev_line, Element containing_block, int width, int x,
            Context c, BlockBox block, boolean last ) {
        c.setFirstLine(false);
        // account for text-align
        TextAlign.adjustTextAlignment(c,line_to_save, containing_block, width, x, last);
        // set the y
        line_to_save.y = prev_line.y + prev_line.height;
        
        // new float code
        line_to_save.x += c.getBlockFormattingContext().getLeftFloatDistance(line_to_save);
        
        
        VerticalAlign.setupVerticalAlign( c, containing_block, line_to_save );
        block.addChild( line_to_save );
    }
    
    
    public Renderer getRenderer() {
        return new InlineRenderer();
    }
    
    public void restyle(Context ctx, Box box) {
        super.restyle(ctx,box);
        if(box instanceof InlineBox) {
            FontUtil.setupTextDecoration(ctx, box.node, (InlineBox)box );
        }
    }


}
/*
* $Id$
*
* $Log$
* Revision 1.26  2004/11/18 16:45:11  joshy
* improved the float code a bit.
* now floats are automatically forced to be blocks
*
*
* Issue number:
* Obtained from:
* Submitted by:
* Reviewed by:
*
* Revision 1.25  2004/11/18 02:37:26  joshy
* moved most of default layout into layout util or box layout
*
* start spliting parts of box layout into the block subpackage
*
* Issue number:
* Obtained from:
* Submitted by:
* Reviewed by:
*
* Revision 1.24  2004/11/15 14:33:09  joshy
* fixed line breaking bug with certain kinds of unbreakable lines
*
* Issue number:
* Obtained from:
* Submitted by:
* Reviewed by:
*
* Revision 1.23  2004/11/14 16:40:58  joshy
* refactored layout factory
*
* Issue number:
* Obtained from:
* Submitted by:
* Reviewed by:
*
* Revision 1.22  2004/11/14 06:26:39  joshy
* added better detection for width problems. should avoid most
* crashes
* Issue number:
* Obtained from:
* Submitted by:
* Reviewed by:
*
* Revision 1.21  2004/11/12 20:25:18  joshy
* added hover support to the browser
* created hover demo
* fixed bug with inline borders
*
* Issue number:
* Obtained from:
* Submitted by:
* Reviewed by:
*
* Revision 1.20  2004/11/09 16:41:33  joshy
* moved text alignment code
* Issue number:
* Obtained from:
* Submitted by:
* Reviewed by:
*
* Revision 1.19  2004/11/09 16:24:29  joshy
* moved float code into separate class
*
* Issue number:
* Obtained from:
* Submitted by:
* Reviewed by:
*
* Revision 1.18  2004/11/09 16:07:57  joshy
* moved vertical align code
*
* Issue number:
* Obtained from:
* Submitted by:
* Reviewed by:
*
* Revision 1.17  2004/11/09 15:53:48  joshy
* initial support for hover (currently disabled)
* moved justification code into it's own class in a new subpackage for inline
* layout (because it's so blooming complicated)
*
* Issue number:
* Obtained from:
* Submitted by:
* Reviewed by:
*
* Revision 1.16  2004/11/09 02:04:23  joshy
* support for text-align: justify
*
* Issue number:
* Obtained from:
* Submitted by:
* Reviewed by:
*
* Revision 1.15  2004/11/08 20:50:59  joshy
* improved float support
*
* Issue number:
* Obtained from:
* Submitted by:
* Reviewed by:
*
* Revision 1.14  2004/11/08 16:56:51  joshy
* added first-line pseudo-class support
*
* Issue number:
* Obtained from:
* Submitted by:
* Reviewed by:
*
* Revision 1.13  2004/11/08 15:10:10  joshy
* added support for styling :first-letter inline boxes
* updated the absolute positioning tests
*
* Issue number:
* Obtained from:
* Submitted by:
* Reviewed by:
*
* Revision 1.12  2004/11/06 22:51:57  joshy
* removed dead code
* Issue number:
* Obtained from:
* Submitted by:
* Reviewed by:
*
* Revision 1.11  2004/11/06 22:49:52  joshy
* cleaned up alice
* initial support for inline borders and backgrounds
* moved all of inlinepainter back into inlinerenderer, where it belongs.
*
*
*
* Issue number:
* Obtained from:
* Submitted by:
* Reviewed by:
*
* Revision 1.10  2004/11/05 16:39:34  joshy
* more float support
* added border bug test
* -j
*
* Issue number:
* Obtained from:
* Submitted by:
* Reviewed by:
*
* Revision 1.9  2004/11/04 15:35:45  joshy
* initial float support
* includes right and left float
* cannot have more than one float per line per side
* floats do not extend beyond enclosing block
*
* Issue number:
* Obtained from:
* Submitted by:
* Reviewed by:
*
* Revision 1.8  2004/11/03 23:54:33  joshy
* added hamlet and tables to the browser
* more support for absolute layout
* added absolute layout unit tests
* removed more dead code and moved code into layout factory
*
*
* Issue number:
* Obtained from:
* Submitted by:
* Reviewed by:
*
* Revision 1.7  2004/11/02 20:44:56  joshy
* put in some prep work for float support
* removed some dead debugging code
* moved isBlock code to LayoutFactory
*
* Issue number:
* Obtained from:
* Submitted by:
* Reviewed by:
*
* Revision 1.6  2004/10/28 13:46:32  joshy
* removed dead code
* moved code about specific elements to the layout factory (link and br)
* fixed form rendering bug
*
* Issue number:
* Obtained from:
* Submitted by:
* Reviewed by:
*
* Revision 1.5  2004/10/27 13:39:56  joshy
* moved more rendering code out of the layouts
*
* Issue number:
* Obtained from:
* Submitted by:
* Reviewed by:
*
* Revision 1.4  2004/10/27 02:00:19  joshy
* removed double spacing from inline layout
*
* Issue number:
* Obtained from:
* Submitted by:
* Reviewed by:
*
* Revision 1.3  2004/10/23 13:46:47  pdoubleya
* Re-formatted using JavaStyle tool.
* Cleaned imports to resolve wildcards except for common packages (java.io, java.util, etc).
* Added CVS log comments at bottom.
*
*
*/
