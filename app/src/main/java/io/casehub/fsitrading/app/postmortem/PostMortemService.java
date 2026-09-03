package io.casehub.fsitrading.app.postmortem;

import io.casehub.blocks.conversation.CommonGroundAnalyser;
import io.casehub.blocks.conversation.CommonGroundState;
import io.casehub.blocks.conversation.ConversationRenderer;
import io.casehub.blocks.conversation.ConversationRendererConfig;
import io.casehub.blocks.conversation.ConversationState;
import io.casehub.blocks.conversation.ConvergenceSignal;
import io.casehub.blocks.conversation.RenderContext;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Map;
import java.util.UUID;

@ApplicationScoped
public class PostMortemService {

    private final ConversationRendererConfig config = FsiConversationRendererConfig.create();
    private final ConversationRenderer renderer = new ConversationRenderer(config);

    public String renderFromState(ConversationState state, ConvergenceSignal convergence) {
        CommonGroundState commonGround = CommonGroundAnalyser.analyse(state, FsiEpistemicRule.INSTANCE);
        RenderContext ctx = new RenderContext(Map.of(), commonGround, convergence, Map.of());
        return renderer.render(state, ctx);
    }
}
