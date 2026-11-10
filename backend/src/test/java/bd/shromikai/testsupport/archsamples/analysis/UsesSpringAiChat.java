package bd.shromikai.testsupport.archsamples.analysis;

import org.springframework.ai.chat.model.ChatModel;

/** Uses a Spring AI chat type outside shared.ai (rule 3). */
public final class UsesSpringAiChat {
  public String name(ChatModel model) {
    return model.getClass().getName();
  }
}
