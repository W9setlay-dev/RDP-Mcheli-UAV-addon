package net.vas.rdp.uav.weapon;

import com.norwood.mcheli.MCH_Explosion;
import com.norwood.mcheli.weapon.MCH_EntityBaseBullet;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.RayTraceResult;

import java.util.UUID;

/**
 * Bridges McHeliCE's synchronous server-side projectile collision and explosion
 * callbacks into the guided mission outcome state machine.
 */
public final class NativeMissileImpactHook {
    private static final ThreadLocal<ImpactContext> ACTIVE_IMPACT = new ThreadLocal<>();

    private NativeMissileImpactHook() { }

    public static void beginImpact(MCH_EntityBaseBullet projectile) {
        if (projectile == null) {
            ACTIVE_IMPACT.remove();
            return;
        }
        ACTIVE_IMPACT.set(new ImpactContext(projectile));
    }

    /** Called after McHeliCE has returned its actual native explosion result. */
    public static void observeNativeExplosion(Entity exploder,
                                               MCH_Explosion.ExplosionResult result) {
        ImpactContext context = ACTIVE_IMPACT.get();
        if (context == null || result == null || exploder == null
                || !context.projectileId.equals(exploder.getUniqueID())) return;
        context.nativeExplosionProcessed = true;
        context.explosionHitEntity = result.hitEntity;
    }

    /**
     * Called on every normal return from McHeliCE onImpact. Its isDead flag is
     * set only on the terminal collision branch; water pass-through and
     * rejected/intercepted contacts therefore do not count as impact.
     */
    public static void finishImpact(MCH_EntityBaseBullet projectile,
                                    RayTraceResult hit) {
        ImpactContext context = ACTIVE_IMPACT.get();
        ACTIVE_IMPACT.remove();
        if (projectile == null || projectile.world == null || projectile.world.isRemote
                || !projectile.isDead || !isWorldCollision(hit)) {
            return;
        }
        boolean nativeExplosionProcessed = context != null
                && context.projectileId.equals(projectile.getUniqueID())
                && context.nativeExplosionProcessed;
        boolean explosionHitEntity = nativeExplosionProcessed && context.explosionHitEntity;
        MissileGuidanceController.observeNativeImpact(projectile,
                nativeExplosionProcessed, explosionHitEntity);
    }

    private static boolean isWorldCollision(RayTraceResult hit) {
        return hit != null && (hit.typeOfHit == RayTraceResult.Type.BLOCK
                || hit.typeOfHit == RayTraceResult.Type.ENTITY);
    }

    private static final class ImpactContext {
        private final UUID projectileId;
        private boolean nativeExplosionProcessed;
        private boolean explosionHitEntity;

        private ImpactContext(MCH_EntityBaseBullet projectile) {
            this.projectileId = projectile.getUniqueID();
        }
    }
}
