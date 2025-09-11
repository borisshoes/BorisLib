package net.borisshoes.borislib.cca;

import net.borisshoes.borislib.callbacks.LoginCallback;
import org.ladysnake.cca.api.v3.component.ComponentV3;

import java.util.List;

public interface ILoginCallbackComponent extends ComponentV3 {
   List<LoginCallback> getCallbacks();
   boolean addCallback(LoginCallback callback);
   boolean removeCallback(LoginCallback callback);
}