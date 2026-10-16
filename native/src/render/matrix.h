#pragma once

#include "core/common.h"

#include <cmath>

namespace at {
    namespace mat4 {
        inline void identity(float *m) {
            std::memset(m, 0, 64);
            m[0] = m[5] = m[10] = m[15] = 1.0f;
        }

        AT_INLINE void multiply(float *m, const float *r) {
            float result[16];
            for (int row = 0; row < 4; row++) {
                float m0 = m[row], m1 = m[4 + row], m2 = m[8 + row], m3 = m[12 + row];
                for (int column = 0; column < 4; column++) {
                    const float *c = r + column * 4;
                    result[column * 4 + row] = m0 * c[0] + m1 * c[1] + m2 * c[2] + m3 * c[3];
                }
            }
            std::memcpy(m, result, 64);
        }

        AT_INLINE void translate(float *m, float x, float y, float z) {
            for (int row = 0; row < 4; row++) m[12 + row] += m[row] * x + m[4 + row] * y + m[8 + row] * z;
        }

        AT_INLINE void scale(float *m, float x, float y, float z) {
            for (int row = 0; row < 4; row++) {
                m[row] *= x;
                m[4 + row] *= y;
                m[8 + row] *= z;
            }
        }

        inline void rotate(float *m, float angle, float x, float y, float z) {
            float length = std::sqrt(x * x + y * y + z * z);
            if (length == 0.0f) return;
            float nx = x / length, ny = y / length, nz = z / length;
            double radians = angle * (3.14159265358979323846 / 180.0);
            auto c = static_cast<float>(std::cos(radians));
            auto s = static_cast<float>(std::sin(radians));
            float k = 1.0f - c;
            float r[16] = {};
            r[0] = nx * nx * k + c;
            r[1] = ny * nx * k + nz * s;
            r[2] = nx * nz * k - ny * s;
            r[4] = nx * ny * k - nz * s;
            r[5] = ny * ny * k + c;
            r[6] = ny * nz * k + nx * s;
            r[8] = nx * nz * k + ny * s;
            r[9] = ny * nz * k - nx * s;
            r[10] = nz * nz * k + c;
            r[15] = 1.0f;
            multiply(m, r);
        }

        inline void ortho(float *m, float left, float right, float bottom, float top, float zNear, float zFar) {
            if (left == right || bottom == top || zNear == zFar) return;
            float r[16] = {};
            r[0] = 2.0f / (right - left);
            r[5] = 2.0f / (top - bottom);
            r[10] = -2.0f / (zFar - zNear);
            r[12] = -(right + left) / (right - left);
            r[13] = -(top + bottom) / (top - bottom);
            r[14] = -(zFar + zNear) / (zFar - zNear);
            r[15] = 1.0f;
            multiply(m, r);
        }

        inline void perspective(float *m, float fovy, float aspect, float zNear, float zFar) {
            float radians = fovy / 2 * 3.14159265358979323846f / 180;
            float deltaZ = zFar - zNear;
            auto sine = static_cast<float>(std::sin(static_cast<double>(radians)));
            if (deltaZ == 0.0f || sine == 0.0f || aspect == 0.0f) return;
            float cotangent = static_cast<float>(std::cos(static_cast<double>(radians))) / sine;
            float r[16] = {};
            r[0] = cotangent / aspect;
            r[5] = cotangent;
            r[10] = -(zFar + zNear) / deltaZ;
            r[11] = -1.0f;
            r[14] = -2 * zNear * zFar / deltaZ;
            multiply(m, r);
        }

        inline bool invert(const float *m, float *out) {
            float a00 = m[0], a01 = m[1], a02 = m[2], a03 = m[3];
            float a10 = m[4], a11 = m[5], a12 = m[6], a13 = m[7];
            float a20 = m[8], a21 = m[9], a22 = m[10], a23 = m[11];
            float a30 = m[12], a31 = m[13], a32 = m[14], a33 = m[15];
            float b00 = a00 * a11 - a01 * a10;
            float b01 = a00 * a12 - a02 * a10;
            float b02 = a00 * a13 - a03 * a10;
            float b03 = a01 * a12 - a02 * a11;
            float b04 = a01 * a13 - a03 * a11;
            float b05 = a02 * a13 - a03 * a12;
            float b06 = a20 * a31 - a21 * a30;
            float b07 = a20 * a32 - a22 * a30;
            float b08 = a20 * a33 - a23 * a30;
            float b09 = a21 * a32 - a22 * a31;
            float b10 = a21 * a33 - a23 * a31;
            float b11 = a22 * a33 - a23 * a32;
            float determinant = b00 * b11 - b01 * b10 + b02 * b09 + b03 * b08 - b04 * b07 + b05 * b06;
            if (determinant == 0.0f) return false;
            float d = 1.0f / determinant;
            out[0] = (a11 * b11 - a12 * b10 + a13 * b09) * d;
            out[1] = (a02 * b10 - a01 * b11 - a03 * b09) * d;
            out[2] = (a31 * b05 - a32 * b04 + a33 * b03) * d;
            out[3] = (a22 * b04 - a21 * b05 - a23 * b03) * d;
            out[4] = (a12 * b08 - a10 * b11 - a13 * b07) * d;
            out[5] = (a00 * b11 - a02 * b08 + a03 * b07) * d;
            out[6] = (a32 * b02 - a30 * b05 - a33 * b01) * d;
            out[7] = (a20 * b05 - a22 * b02 + a23 * b01) * d;
            out[8] = (a10 * b10 - a11 * b08 + a13 * b06) * d;
            out[9] = (a01 * b08 - a00 * b10 - a03 * b06) * d;
            out[10] = (a30 * b04 - a31 * b02 + a33 * b00) * d;
            out[11] = (a21 * b02 - a20 * b04 - a23 * b00) * d;
            out[12] = (a11 * b07 - a10 * b09 - a12 * b06) * d;
            out[13] = (a00 * b09 - a01 * b07 + a02 * b06) * d;
            out[14] = (a31 * b01 - a30 * b03 - a32 * b00) * d;
            out[15] = (a20 * b03 - a21 * b01 + a22 * b00) * d;
            return true;
        }

        inline void normalMatrix(const float *m, bool rescale, float *out) {
            float a = m[0], b = m[4], c = m[8];
            float d = m[1], e = m[5], f = m[9];
            float g = m[2], h = m[6], i = m[10];
            float c00 = e * i - f * h;
            float c01 = f * g - d * i;
            float c02 = d * h - e * g;
            float determinant = a * c00 + b * c01 + c * c02;
            float s = determinant == 0.0f ? 0.0f : 1.0f / determinant;
            float i00 = c00 * s, i01 = (c * h - b * i) * s, i02 = (b * f - c * e) * s;
            float i10 = c01 * s, i11 = (a * i - c * g) * s, i12 = (c * d - a * f) * s;
            float i20 = c02 * s, i21 = (b * g - a * h) * s, i22 = (a * e - b * d) * s;
            out[0] = i00;
            out[1] = i01;
            out[2] = i02;
            out[4] = i10;
            out[5] = i11;
            out[6] = i12;
            out[8] = i20;
            out[9] = i21;
            out[10] = i22;
            out[7] = 0.0f;
            out[11] = 0.0f;
            float length = std::sqrt(i20 * i20 + i21 * i21 + i22 * i22);
            out[3] = rescale && length != 0.0f ? 1.0f / length : 1.0f;
        }

        inline void transformPlane(const float *plane, const float *inverse, float *out) {
            float x = plane[0], y = plane[1], z = plane[2], w = plane[3];
            for (int column = 0; column < 4; column++) {
                const float *c = inverse + column * 4;
                out[column] = x * c[0] + y * c[1] + z * c[2] + w * c[3];
            }
        }

        inline void transformPoint(const float *m, float x, float y, float z, float w, float *out) {
            for (int row = 0; row < 4; row++) out[row] = m[row] * x + m[4 + row] * y + m[8 + row] * z + m[12 + row] * w;
        }
    } // namespace mat4

    class MatrixStack {
    public:
        explicit MatrixStack(int depth) : depth(depth), data(new float[depth * 16]) { identity(); }
        ~MatrixStack() { delete[] data; }

        MatrixStack(const MatrixStack &) = delete;

        MatrixStack &operator=(const MatrixStack &) = delete;

        AT_INLINE float *top() { return data + offset; }
        [[nodiscard]] AT_INLINE const float *top() const { return data + offset; }

        bool push() {
            if (offset + 16 >= depth * 16) return false;
            std::memcpy(data + offset + 16, data + offset, 64);
            offset += 16;
            return true;
        }

        bool pop() {
            if (offset == 0) return false;
            offset -= 16;
            return true;
        }

        void identity() { mat4::identity(top()); }

    private:
        int depth;
        float *data;
        int offset = 0;
    };
} // namespace at
