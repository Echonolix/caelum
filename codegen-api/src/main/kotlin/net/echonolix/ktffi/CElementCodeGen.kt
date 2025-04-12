package net.echonolix.ktffi

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.MemberName
import com.squareup.kotlinpoet.MemberName.Companion.member

context(ctx: KTFFICodegenContext)
fun CElement.TopLevel.packageName(): String {
    return ctx.resolvePackageName(this)
}

context(ctx: KTFFICodegenContext)
fun CElement.TopLevel.className(): ClassName {
    return ClassName(packageName(), name)
}