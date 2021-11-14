<!DOCTYPE html>
<html lang="en">

<head>
    <#include "includes/header.ftlh">
    <link href="vendor/datatables/dataTables.bootstrap4.min.css" rel="stylesheet">
    <script src="js/autocomplete.js"/>
</head>

<body id="page-top">

<!-- Page Wrapper -->
<div id="wrapper">

    <#include "includes/sidebar.ftlh">

    <!-- Content Wrapper -->
    <div id="content-wrapper" class="d-flex flex-column">
        <#include "includes/topbar.ftlh"/>
        <!-- Main Content -->
        <div id="content">

            <!-- Begin Page Content -->
            <div class="container-fluid">
                <div class="row">
                    <div class="col-lg-6 mb-4">
                        <div class="card bg-primary text-white shadow">
                            <div class="card-body">
                                Elements
                                <div class="text-white-50 small">12123</div>
                            </div>
                        </div>
                    </div>
                    <div class="col-lg-6 mb-4">
                        <div class="card bg-success text-white shadow">
                            <div class="card-body">
                                avg. Size
                                <div class="text-white-50 small">1232</div>
                            </div>
                        </div>
                    </div>
                </div>
                <div class="row">
                    <!-- Topbar Search -->
                    <form
                            class="d-sm-inline-block border-1 form-inline mr-auto ml-md-3 my-2 my-md-0 mw-100">
                        <div class="input-group">
                            <input type="text" class="form-control bg-light border-2 small" id="input" placeholder="Start typing..." autocomplete="off">

                        <label for="form-autocomplete-4" class="active">Query....</label>
                        </div>
                    </form>
                </div>
            </div>
            <!-- /.container-fluid -->

        </div>
        <!-- End of Main Content -->

        <!-- Footer -->
        <#include "includes/footer.ftlh"/>
        <!-- End of Footer -->

    </div>
    <!-- End of Content Wrapper -->

</div>
<!-- End of Page Wrapper -->

<!-- Scroll to Top Button-->
<a class="scroll-to-top rounded" href="#page-top">
    <i class="fas fa-angle-up"></i>
</a>


<!-- Bootstrap core JavaScript-->
<script src="/vendor/jquery/jquery.min.js"></script>
<script src="/vendor/bootstrap/js/bootstrap.bundle.min.js"></script>

<!-- Core plugin JavaScript-->
<script src="/vendor/jquery-easing/jquery.easing.min.js"></script>

<!-- Custom scripts for all pages-->
<script src="/js/sb-admin-2.min.js"></script>
<script src="vendor/datatables/jquery.dataTables.min.js"></script>
<script src="vendor/datatables/dataTables.bootstrap4.min.js"></script>
<script>
    const field = document.getElementById('input');
    const ac = new Autocomplete(field, {
        data: [{label: "I'm a label", value: 42}],
        maximumItems: 5,
        treshold: 1,
        onSelectItem: ({label, value}) => {
            console.log("user selected:", label, value);
        }
    });

    ac.setData([
        {
            "label": "db.test123.find({",
            "value": "db.test123.find({"
        },
        {
            "label": "db.test123.remove({",
            "value": "db.test123.remove({"
        },
        {
            "label": "db.test123.drop()",
            "value": "db.test123.drop()"
        },
        {
            "label": "db.stats()",
            "value": "db.stats()"
        },
        {
            "label": "sh.status()",
            "value": "sh.status()"
        },


    ]);
</script>

</body>

</html>
