package org.commonmark.renderer.html;

import org.commonmark.node.*;
import org.commonmark.renderer.NodeRenderer;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * The node renderer that renders all the core nodes (comes last in the order of node renderers).
 */
public class CoreHtmlNodeRenderer extends AbstractVisitor implements NodeRenderer {

    protected final HtmlNodeRendererContext context;
    private final HtmlWriter html;

    public CoreHtmlNodeRenderer(HtmlNodeRendererContext context) {
        this.context = context;
        this.html = context.getWriter();
    }

    @Override
    public Set<Class<? extends Node>> getNodeTypes() {
        return Set.of(
                Document.class,
                Heading.class,
                Paragraph.class,
                BlockQuote.class,
                BulletList.class,
                FencedCodeBlock.class,
                HtmlBlock.class,
                ThematicBreak.class,
                IndentedCodeBlock.class,
                Link.class,
                ListItem.class,
                OrderedList.class,
                Image.class,
                Emphasis.class,
                StrongEmphasis.class,
                Text.class,
                Code.class,
                HtmlInline.class,
                SoftLineBreak.class,
                HardLineBreak.class
        );
    }

    @Override
    public void render(Node node) {
        node.accept(this);
    }

    @Override
    public void visit(Document document) {
        // No rendering itself
        visitChildren(document);
    }

    @Override
    public void visit(Heading heading) {
        String htag = "h" + heading.getLevel();
        html.line();
        html.tag(htag, getAttrs(heading, htag));
        visitChildren(heading);
        html.tag('/' + htag);
        html.line();
    }

    @Override
    public void visit(Paragraph paragraph) {
        boolean omitP = isInTightList(paragraph) || //
                (context.shouldOmitSingleParagraphP() && paragraph.getParent() instanceof Document && //
                        paragraph.getPrevious() == null && paragraph.getNext() == null);
        if (!omitP) {
            html.line();
            html.tag("p", getAttrs(paragraph, "p"));
        }
        visitChildren(paragraph);
        if (!omitP) {
            html.tag("/p");
            html.line();
        }
    }

    @Override
    public void visit(BlockQuote blockQuote) {
        html.line();
        html.tag("blockquote", getAttrs(blockQuote, "blockquote"));
        html.line();
        visitChildren(blockQuote);
        html.line();
        html.tag("/blockquote");
        html.line();
    }

    @Override
    public void visit(BulletList bulletList) {
        renderListBlock(bulletList, "ul", getAttrs(bulletList, "ul"));
    }

    @Override
    public void visit(FencedCodeBlock fencedCodeBlock) {
        String literal = fencedCodeBlock.getLiteral();
        Map<String, String> attributes = new LinkedHashMap<>();
        String info = fencedCodeBlock.getInfo();
        if (info != null && !info.isEmpty()) {
            int space = info.indexOf(" ");
            String language;
            if (space == -1) {
                language = info;
            } else {
                language = info.substring(0, space);
            }
            attributes.put("class", "language-" + language);
        }
        renderCodeBlock(literal, fencedCodeBlock, attributes);
    }

    @Override
    public void visit(HtmlBlock htmlBlock) {
        html.line();
        if (context.shouldEscapeHtml()) {
            html.tag("p", getAttrs(htmlBlock, "p"));
            html.text(htmlBlock.getLiteral());
            html.tag("/p");
        } else {
            html.raw(htmlBlock.getLiteral());
        }
        html.line();
    }

    @Override
    public void visit(ThematicBreak thematicBreak) {
        html.line();
        html.tag("hr", getAttrs(thematicBreak, "hr"), true);
        html.line();
    }

    @Override
    public void visit(IndentedCodeBlock indentedCodeBlock) {
        renderCodeBlock(indentedCodeBlock.getLiteral(), indentedCodeBlock, Map.of());
    }

    @Override
    public void visit(Link link) {
        Map<String, String> attrs = new LinkedHashMap<>();
        String url = link.getDestination();

        if (context.shouldSanitizeUrls()) {
            url = context.urlSanitizer().sanitizeLinkUrl(url);
            attrs.put("rel", "nofollow");
        }

        url = context.encodeUrl(url);
        attrs.put("href", url);
        if (link.getTitle() != null) {
            attrs.put("title", link.getTitle());
        }
        html.tag("a", getAttrs(link, "a", attrs));
        visitChildren(link);
        html.tag("/a");
    }

    @Override
    public void visit(ListItem listItem) {
        html.tag("li", getAttrs(listItem, "li"));
        visitChildren(listItem);
        html.tag("/li");
        html.line();
    }

    @Override
    public void visit(OrderedList orderedList) {
        int start = orderedList.getMarkerStartNumber() != null ? orderedList.getMarkerStartNumber() : 1;
        Map<String, String> attrs = new LinkedHashMap<>();
        if (start != 1) {
            attrs.put("start", String.valueOf(start));
        }
        renderListBlock(orderedList, "ol", getAttrs(orderedList, "ol", attrs));
    }

    @Override
    public void visit(Image image) {
        String url = image.getDestination();

        AltTextVisitor altTextVisitor = new AltTextVisitor();
        image.accept(altTextVisitor);
        String altText = altTextVisitor.getAltText();

        Map<String, String> attrs = new LinkedHashMap<>();
        if (context.shouldSanitizeUrls()) {
            url = context.urlSanitizer().sanitizeImageUrl(url);
        }

        attrs.put("src", context.encodeUrl(url));
        attrs.put("alt", altText);
        if (image.getTitle() != null) {
            attrs.put("title", image.getTitle());
        }

        html.tag("img", getAttrs(image, "img", attrs), true);
    }

    @Override
    public void visit(Emphasis emphasis) {
        html.tag("em", getAttrs(emphasis, "em"));
        visitChildren(emphasis);
        html.tag("/em");
    }

    @Override
    public void visit(StrongEmphasis strongEmphasis) {
        html.tag("strong", getAttrs(strongEmphasis, "strong"));
        visitChildren(strongEmphasis);
        html.tag("/strong");
    }

    @Override
    public void visit(Text text) {
        html.text(text.getLiteral());
    }

    @Override
    public void visit(Code code) {
        html.tag("code", getAttrs(code, "code"));
        html.text(code.getLiteral());
        html.tag("/code");
    }

    @Override
    public void visit(HtmlInline htmlInline) {
        if (context.shouldEscapeHtml()) {
            html.text(htmlInline.getLiteral());
        } else {
            html.raw(htmlInline.getLiteral());
        }
    }

    @Override
    public void visit(SoftLineBreak softLineBreak) {
        html.raw(context.getSoftbreak());
    }

    @Override
    public void visit(HardLineBreak hardLineBreak) {
        html.tag("br", getAttrs(hardLineBreak, "br"), true);
        html.line();
    }

    @Override
    protected void visitChildren(Node parent) {
        Node node = parent.getFirstChild();
        while (node != null) {
            context.render(node);
            node = node.getNext();
        }
    }

    private void renderCodeBlock(String literal, Node node, Map<String, String> attributes) {
        html.line();
        html.tag("pre", getAttrs(node, "pre"));
        html.tag("code", getAttrs(node, "code", attributes));
        html.text(literal);
        html.tag("/code");
        html.tag("/pre");
        html.line();
    }

    private void renderListBlock(ListBlock listBlock, String tagName, Map<String, String> attributes) {
        html.line();
        html.tag(tagName, attributes);
        html.line();
        visitChildren(listBlock);
        html.line();
        html.tag('/' + tagName);
        html.line();
    }

    private boolean isInTightList(Paragraph paragraph) {
        Node parent = paragraph.getParent();
        if (parent != null) {
            Node gramps = parent.getParent();
            if (gramps instanceof ListBlock) {
                ListBlock list = (ListBlock) gramps;
                return list.isTight();
            }
        }
        return false;
    }

    private Map<String, String> getAttrs(Node node, String tagName) {
        return getAttrs(node, tagName, Map.of());
    }

    private Map<String, String> getAttrs(Node node, String tagName, Map<String, String> defaultAttributes) {
        return context.extendAttributes(node, tagName, defaultAttributes);
    }

    private static class AltTextVisitor extends AbstractVisitor {

        private final StringBuilder sb = new StringBuilder();

        String getAltText() {
            return sb.toString();
        }

        @Override
        public void visit(Text text) {
            sb.append(text.getLiteral());
        }

        @Override
        public void visit(SoftLineBreak softLineBreak) {
            sb.append('\n');
        }

        @Override
        public void visit(HardLineBreak hardLineBreak) {
            sb.append('\n');
        }
    }
}
