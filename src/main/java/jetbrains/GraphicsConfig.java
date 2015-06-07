package jetbrains;
/* BTO: from http://bulenkov.com/2013/06/23/retina-support-in-oracle-jdk-1-7/ */
/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import java.awt.*;
import java.util.Map;
public class GraphicsConfig {
  private final Graphics2D myG;
  private final Map myHints;
  public GraphicsConfig(Graphics g) {
    myG = (Graphics2D)g;
    myHints = (Map)myG.getRenderingHints().clone();
  }
  public GraphicsConfig setAntialiasing(boolean on) {
    myG.setRenderingHint(RenderingHints.KEY_ANTIALIASING, on ? RenderingHints.VALUE_ANTIALIAS_ON : RenderingHints.VALUE_ANTIALIAS_OFF);
    return this;
  }
  public Graphics2D getG() {
    return myG;
  }
  public void restore() {
    myG.setRenderingHints(myHints);
  }
}