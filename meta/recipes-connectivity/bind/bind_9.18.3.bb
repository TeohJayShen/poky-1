SUMMARY = "ISC Internet Domain Name Server"
HOMEPAGE = "https://www.isc.org/bind/"
DESCRIPTION = "BIND 9 provides a full-featured Domain Name Server system"
SECTION = "console/network"

LICENSE = "MPL-2.0"
LIC_FILES_CHKSUM = "file://COPYRIGHT;md5=9a4a897f202c0710e07f2f2836bc2b62"

DEPENDS = "openssl libcap zlib libuv"

SRC_URI = "https://ftp.isc.org/isc/bind9/${PV}/${BPN}-${PV}.tar.xz \
           file://conf.patch \
           file://named.service \
           file://bind9 \
           file://generate-rndc-key.sh \
           file://make-etc-initd-bind-stop-work.patch \
           file://init.d-add-support-for-read-only-rootfs.patch \
           file://bind-ensure-searching-for-json-headers-searches-sysr.patch \
           file://0001-named-lwresd-V-and-start-log-hide-build-options.patch \
           file://0001-avoid-start-failure-with-bind-user.patch \
           "

SRC_URI[sha256sum] = "0ad8da773bd93cba0ef66cc81999698ebdf9c3e51faed5e5c8c1eb75cad2ae6f"

UPSTREAM_CHECK_URI = "https://ftp.isc.org/isc/bind9/"
# follow the ESV versions divisible by 2
UPSTREAM_CHECK_REGEX = "(?P<pver>9.(\d*[02468])+(\.\d+)+(-P\d+)*)/"

# Issue only affects dhcpd with recent bind versions. We don't ship dhcpd anymore
# so the issue doesn't affect us.
CVE_CHECK_IGNORE += "CVE-2019-6470"

inherit autotools update-rc.d systemd useradd pkgconfig multilib_header update-alternatives

# PACKAGECONFIGs readline and libedit should NOT be set at same time
PACKAGECONFIG ?= "readline"
PACKAGECONFIG[httpstats] = "--with-libxml2=${STAGING_DIR_HOST}${prefix},--without-libxml2,libxml2"
PACKAGECONFIG[readline] = "--with-readline=readline,,readline"
PACKAGECONFIG[libedit] = "--with-readline=libedit,,libedit"
PACKAGECONFIG[dns-over-http] = "--enable-doh,--disable-doh,nghttp2"

EXTRA_OECONF = " --disable-devpoll --disable-auto-validation --enable-epoll \
                 --with-gssapi=no --with-lmdb=no --with-zlib \
                 --sysconfdir=${sysconfdir}/bind \
                 --with-openssl=${STAGING_DIR_HOST}${prefix} \
               "
LDFLAGS:append = " -lz"

inherit ${@bb.utils.contains('PACKAGECONFIG', 'python3', 'python3native setuptools3-base', '', d)}

# dhcp needs .la so keep them
REMOVE_LIBTOOL_LA = "0"

USERADD_PACKAGES = "${PN}"
USERADD_PARAM:${PN} = "--system --home ${localstatedir}/cache/bind --no-create-home \
                       --user-group bind"

INITSCRIPT_NAME = "bind"
INITSCRIPT_PARAMS = "defaults"

SYSTEMD_SERVICE:${PN} = "named.service"

do_install:append() {

	install -d -o bind "${D}${localstatedir}/cache/bind"
	install -d "${D}${sysconfdir}/bind"
	install -d "${D}${sysconfdir}/init.d"
	install -m 644 ${S}/conf/* "${D}${sysconfdir}/bind/"
	install -m 755 "${S}/init.d" "${D}${sysconfdir}/init.d/bind"
        if ${@bb.utils.contains('PACKAGECONFIG', 'python3', 'true', 'false', d)}; then
		sed -i -e '1s,#!.*python3,#! /usr/bin/python3,' \
		${D}${sbindir}/dnssec-coverage \
		${D}${sbindir}/dnssec-checkds \
		${D}${sbindir}/dnssec-keymgr
	fi

	# Install systemd related files
	install -d ${D}${sbindir}
	install -m 755 ${WORKDIR}/generate-rndc-key.sh ${D}${sbindir}
	install -d ${D}${systemd_system_unitdir}
	install -m 0644 ${WORKDIR}/named.service ${D}${systemd_system_unitdir}
	sed -i -e 's,@BASE_BINDIR@,${base_bindir},g' \
	       -e 's,@SBINDIR@,${sbindir},g' \
	       ${D}${systemd_system_unitdir}/named.service

	install -d ${D}${sysconfdir}/default
	install -m 0644 ${WORKDIR}/bind9 ${D}${sysconfdir}/default

	if ${@bb.utils.contains('DISTRO_FEATURES', 'systemd', 'true', 'false', d)}; then
		install -d ${D}${sysconfdir}/tmpfiles.d
		echo "d /run/named 0755 bind bind - -" > ${D}${sysconfdir}/tmpfiles.d/bind.conf
	fi
}

CONFFILES:${PN} = " \
	${sysconfdir}/bind/named.conf \
	${sysconfdir}/bind/named.conf.local \
	${sysconfdir}/bind/named.conf.options \
	${sysconfdir}/bind/db.0 \
	${sysconfdir}/bind/db.127 \
	${sysconfdir}/bind/db.empty \
	${sysconfdir}/bind/db.local \
	${sysconfdir}/bind/db.root \
	"

ALTERNATIVE:${PN}-utils = "nslookup"
ALTERNATIVE_LINK_NAME[nslookup] = "${bindir}/nslookup"
ALTERNATIVE_PRIORITY = "100"

PACKAGE_BEFORE_PN += "${PN}-utils"
FILES:${PN}-utils = "${bindir}/host ${bindir}/dig ${bindir}/mdig ${bindir}/nslookup ${bindir}/nsupdate"
FILES:${PN}-dev += "${bindir}/isc-config.h"
FILES:${PN} += "${sbindir}/generate-rndc-key.sh"

PACKAGE_BEFORE_PN += "${PN}-libs"
# special arrangement below due to
# https://github.com/isc-projects/bind9/commit/0e25af628cd776f98c04fc4cc59048f5448f6c88
FILES_SOLIBSDEV = "${libdir}/*[!0-9].so ${libdir}/libbind9.so"
FILES:${PN}-libs = "${libdir}/named/*.so* ${libdir}/*-${PV}.so"
FILES:${PN}-staticdev += "${libdir}/*.la"

PACKAGE_BEFORE_PN += "${@bb.utils.contains('PACKAGECONFIG', 'python3', 'python3-bind', '', d)}"
FILES:python3-bind = "${sbindir}/dnssec-coverage ${sbindir}/dnssec-checkds \
                ${sbindir}/dnssec-keymgr ${PYTHON_SITEPACKAGES_DIR}"

RDEPENDS:${PN}-dev = ""
RDEPENDS:python3-bind = "python3-core python3-ply"
