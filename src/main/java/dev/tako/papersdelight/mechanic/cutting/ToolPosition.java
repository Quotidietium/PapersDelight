package dev.tako.papersdelight.mechanic.cutting;

public record ToolPosition(
        Double translateX,
        Double translateY,
        Double translateZ,
        Double scale,
        Double rotationY,
        Double rotationPitch,
        Double rotationRoll
) {

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        Double tx, ty, tz, scale, ry, rp, rr;
        public Builder tx  (Double v) { this.tx = v;    return this; }
        public Builder ty  (Double v) { this.ty = v;    return this; }
        public Builder tz  (Double v) { this.tz = v;    return this; }
        public Builder scale(Double v) { this.scale = v; return this; }
        public Builder ry  (Double v) { this.ry = v;    return this; }
        public Builder rp  (Double v) { this.rp = v;    return this; }
        public Builder rr  (Double v) { this.rr = v;    return this; }
        public ToolPosition build() { return new ToolPosition(tx, ty, tz, scale, ry, rp, rr); }
    }
}
