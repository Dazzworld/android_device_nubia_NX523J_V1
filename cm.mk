#
# Copyright 2015 The CyanogenMod Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

# Inherit from those products. Most specific first.
$(call inherit-product, $(SRC_TARGET_DIR)/product/core_64_bit.mk)
$(call inherit-product, $(SRC_TARGET_DIR)/product/full_base_telephony.mk)

# Inherit some common CM stuff.
$(call inherit-product, vendor/cm/config/common_full_phone.mk)

# Inherit from kenzo device
$(call inherit-product, device/nubia/NX523J_V1/device.mk)

# Set those variables here to overwrite the inherited values.
BOARD_VENDOR := nubia
PRODUCT_BRAND := nubia
PRODUCT_DEVICE := NX523J_V1
PRODUCT_NAME := cm_NX523J_V1
PRODUCT_MANUFACTURER := nubia
PRODUCT_MODEL := NX523J_V1
TARGET_VENDOR := nubia

PRODUCT_GMS_CLIENTID_BASE := android-nubia

TARGET_VENDOR_PRODUCT_NAME := NX523J_V1
TARGET_VENDOR_DEVICE_NAME := NX523J_V1
PRODUCT_BUILD_PROP_OVERRIDES += TARGET_DEVICE=NX523J_V1 PRODUCT_NAME=NX523J_V1

# Use the latest approved GMS identifiers unless running a signed build
ifneq ($(SIGN_BUILD),true)
PRODUCT_BUILD_PROP_OVERRIDES += \
    BUILD_FINGERPRINT=nubia/NX529J/NX529J:5.1.1/LMY47V/eng.nubia.20160927.144351:user/release-keys \
    PRIVATE_BUILD_DESC="NX529J-user 5.1.1 LMY47V eng.nubia.20160927.144351 release-keys"
endif
