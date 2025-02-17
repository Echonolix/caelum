package org.echonolix.ktffi

import com.squareup.kotlinpoet.ClassName

object KTFFICodegen {
    const val packageName = "org.echonolix.ktffi"
    val typeCname = ClassName(packageName, "Type")
    val structCname = ClassName(packageName, "Struct")
    val unionCname = ClassName(packageName, "Union")
    val arrayCname = ClassName(packageName, "Array")
    val valueCname = ClassName(packageName, "Value")
    val pointerCname = ClassName(packageName, "Pointer")
}