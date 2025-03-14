class A {

  static class Inner {
    public String s1;
  }

  void method() {
    String s = "";
    Inner inner = new Inner();
    int i = 0;
    for(;i++;i<10){
      s = i + " : "; //Non-Compliant
      s += i;//Non-Compliant
      inner.s1 = i + " : "; //Non-Compliant
    }
    while(i<20){
      s = i + " : "; //Non-Compliant
      inner.s1 = i + " : "; //Non-Compliant
      s = ((i + " : ")); //Non-Compliant
      inner = new Inner();
      i = i + 1;
    }
    s = "a" + "b";
    for(int i=0;i<10;i++){
      MyObject myObject = new MyObject();
      myObject.stringProperty = "a" + "b";  //Compliant, var is local in the loop
      myObject.stringProperty += "a";//Compliant, var is local in the loop
      MyObject.newInstance().stringProperty = "a"+"b"; //False positive.
      MyObject.newInstance().stringProperty += "b"; //Non-Compliant
    }
  }

  class MyObject {
    String stringProperty;
    static MyObject newInstance(){
      return new MyObject();
    }
  }

}