package io.casehub.fsitrading.app.postmortem;

import io.casehub.agency.conversation.CommonGroundAnalyser;
import io.casehub.agency.conversation.CommonGroundState;
import io.casehub.agency.conversation.ConversationRenderer;
import io.casehub.agency.conversation.ConversationRendererConfig;
import io.casehub.agency.conversation.ConversationState;
import io.casehub.agency.conversation.ConvergenceSignal;
import io.casehub.agency.conversation.RenderContext;
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
