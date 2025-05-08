package org.bookwormpi.clientsidetesting.client.hud;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector4f;

/**
 * Utility class to convert 3D world coordinates to 2D screen coordinates
 */
public class WorldToScreen {
    
    /**
     * Projects a 3D world position to a 2D screen position
     * 
     * @param worldPos The world position to project
     * @param screenWidth The screen width
     * @param screenHeight The screen height
     * @return The screen position, or null if the point is outside the screen
     */
    public static Vec3d project(Vec3d worldPos, int screenWidth, int screenHeight) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.gameRenderer == null || client.getEntityRenderDispatcher().camera == null) {
            return null;
        }
        
        // Get the camera information
        Camera camera = client.gameRenderer.getCamera();
        Vec3d cameraPos = camera.getPos();
        
        // Create a projection matrix
        MatrixStack projectionStack = new MatrixStack();
        projectionStack.peek().getPositionMatrix().set(client.gameRenderer.getBasicProjectionMatrix(0.05F));
        Matrix4f projectionMatrix = projectionStack.peek().getPositionMatrix();
        
        // Create a view matrix from the camera rotation
        MatrixStack viewStack = new MatrixStack();
        viewStack.multiply(camera.getRotation());
        Matrix4f viewMatrix = viewStack.peek().getPositionMatrix();
        
        // Create the position vector in camera space
        Vector4f pos = new Vector4f(
                (float)(worldPos.x - cameraPos.x),
                (float)(worldPos.y - cameraPos.y),
                (float)(worldPos.z - cameraPos.z),
                1.0f
        );
        
        // Apply view-projection transformations
        pos.mul(viewMatrix);
        pos.mul(projectionMatrix);
        
        // Perspective division (convert to normalized device coordinates)
        if (pos.w <= 0.0f) {
            return null; // Behind the camera
        }
        
        pos.x /= pos.w;
        pos.y /= pos.w;
        pos.z /= pos.w;
        
        // Check if the point is visible (in normalized device coordinates, visible range is [-1, 1])
        if (pos.x < -1.0f || pos.x > 1.0f || pos.y < -1.0f || pos.y > 1.0f) {
            return null; // Outside the viewport
        }
        
        // Convert from normalized device coordinates to screen coordinates
        float screenX = (pos.x + 1.0f) / 2.0f * screenWidth;
        float screenY = (1.0f - pos.y) / 2.0f * screenHeight; // Y is flipped in NDC
        
        return new Vec3d(screenX, screenY, pos.z);
    }
}